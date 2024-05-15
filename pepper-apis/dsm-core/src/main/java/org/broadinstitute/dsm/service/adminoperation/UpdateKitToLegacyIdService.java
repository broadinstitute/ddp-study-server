package org.broadinstitute.dsm.service.adminoperation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.kitrequest.KitRequestDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.Profile;
import org.broadinstitute.dsm.service.admin.AdminOperationRecord;
import org.broadinstitute.dsm.service.elastic.ElasticSearchService;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

@Slf4j
/**
 * Admin Operation to change kits collaborator ids and ddpParticipantI to participant's legacy IDs
 */
public class UpdateKitToLegacyIdService extends ParticipantAdminOperationService {
    private DDPInstanceDto ddpInstanceDto;
    private LegacyKitUpdateCollabIdList legacyKitUpdateCollabIdList;
    private static final KitRequestDao kitRequestDao = new KitRequestDao();
    private final DDPInstanceDao ddpInstanceDao = new DDPInstanceDao();
    private static final ElasticSearchService elasticSearchService = new ElasticSearchService();

    /**
     * Validate input and initialize operation, synchronously
     *
     * @param userId ID of user performing operation
     * @param realm study realm for operation (approved realm role for userId)
     * @param attributes key-values, if any
     * @param payload request, if any
     * @throws Exception for any errors
     */
    @Override
    public void initialize(String userId, String realm, Map<String, String> attributes, String payload) {
        ddpInstanceDto = ddpInstanceDao.getDDPInstanceByInstanceName(realm)
                .orElseThrow(() -> new DSMBadRequestException("Invalid realm: " + realm));

        if (StringUtils.isBlank(payload)) {
            throw new DSMBadRequestException("Missing required payload");
        }

        // get and verify content of payload
        legacyKitUpdateCollabIdList = LegacyKitUpdateCollabIdList.fromJson(payload);

    }

    /**
     * Run operation, typically asynchronously
     *
     * @param operationId ID for reporting results
     */
    @Override
    public void run(int operationId) {
        List<UpdateLog> updateLog = new ArrayList<>();

        legacyKitUpdateCollabIdList.getUpdateCollabIdRequests().forEach(updateKitToLegacyIdsRequest ->
                updateLog.add(changeKitIdsToLegacyIds(updateKitToLegacyIdsRequest, ddpInstanceDto)));

        // update job log record
        try {
            String json = ObjectMapperSingleton.writeValueAsString(updateLog);
            AdminOperationRecord.updateOperationRecord(operationId, AdminOperationRecord.OperationStatus.COMPLETED, json);
        } catch (Exception e) {
            log.error("Error writing operation log: {}", e.toString());
        }
    }

    /**
     * <p>
     * First runs some validations on the request, checks that the participant exists in the ES with the given short ID, that the given
     * legacy short ID matches the one on file, that the kit request exists for the given collaborator sample ID to be updated,
     * and also that the new collaborator sample ID does not already exist.
     * </p><p>
     * If all validations pass, it updates the kit request in the database and the participant's kit requests in the ES.
     * </p>
     * */
    protected static UpdateLog changeKitIdsToLegacyIds(UpdateKitToLegacyIdsRequest updateKitToLegacyIdsRequest,
                                                       DDPInstanceDto ddpInstanceDto) {
        String shortId = updateKitToLegacyIdsRequest.getShortId();
        String currentCollaboratorSampleId = updateKitToLegacyIdsRequest.getCurrentCollaboratorSampleId();
        String newCollaboratorSampleId = updateKitToLegacyIdsRequest.getNewCollaboratorSampleId();
        // Check if the participant exists in ES and if it has a legacy short ID
        Profile profile;
        try {
            profile = elasticSearchService.getParticipantProfileByShortID(shortId, ddpInstanceDto.getEsParticipantIndex());
        } catch (Exception e) {
            return new UpdateLog(shortId, UpdateLog.UpdateStatus.ERROR, e.getMessage());
        }
        String legacyShortIdOnFile = profile.getLegacyShortId();
        String legacyParticipantId = profile.getLegacyAltPid();
        if (StringUtils.isBlank(legacyShortIdOnFile) || StringUtils.isBlank(legacyParticipantId)) {
            return new UpdateLog(shortId, UpdateLog.UpdateStatus.ERROR,
                    "Participant does not have legacy IDs, will not update kit %s".formatted(currentCollaboratorSampleId));
        }
        if (!updateKitToLegacyIdsRequest.getLegacyShortId().equals(legacyShortIdOnFile)) {
            return new UpdateLog(shortId, UpdateLog.UpdateStatus.ERROR,
                    ("Legacy short ID %s does not match legacy short ID on file %s for participant short ID %s, "
                    + " will not update kit %s").formatted(updateKitToLegacyIdsRequest.getLegacyShortId(), legacyShortIdOnFile, shortId,
                    currentCollaboratorSampleId));
        }
        if (!existsKitRequestWithCollaboratorSampleId(currentCollaboratorSampleId, profile.getGuid(), kitRequestDao)) {
            return new UpdateLog(shortId, UpdateLog.UpdateStatus.ERROR,
                    ("Kit request not found for collaborator sample ID %s".formatted(currentCollaboratorSampleId)));
        }
        if (existsKitRequestWithCollaboratorSampleId(newCollaboratorSampleId, profile.getGuid(), kitRequestDao)) {
            return new UpdateLog(shortId, UpdateLog.UpdateStatus.ERROR,
                    "Kit request with the new collaboratorSampleId %s already exists!".formatted(newCollaboratorSampleId));
        }
        try {
            String ddpParticipantId = profile.getGuid();
            kitRequestDao.updateKitToLegacyIds(updateKitToLegacyIdsRequest, legacyParticipantId);
            changeDataInEs(currentCollaboratorSampleId, newCollaboratorSampleId, ddpParticipantId, ddpInstanceDto.getEsParticipantIndex());
            return new UpdateLog(ddpParticipantId, UpdateLog.UpdateStatus.ES_UPDATED,
                    "Kit collab id was changed from %s to %s".formatted(currentCollaboratorSampleId, newCollaboratorSampleId));
        } catch (Exception e) {
            return new UpdateLog(shortId, UpdateLog.UpdateStatus.ERROR,
                    "Error updating kit: " + currentCollaboratorSampleId + e.toString());
        }
    }

    private static void changeDataInEs(String currentCollaboratorSampleId, String newCollaboratorSampleId, String ddpParticipantId,
                                       String esIndex) {
        Dsm dsm = elasticSearchService.getRequiredDsmData(ddpParticipantId, esIndex);
        List<KitRequestShipping> kitRequestShippings = dsm.getKitRequestShipping();
        if (kitRequestShippings != null) {
            kitRequestShippings.removeIf(
                    kitRequestShipping -> currentCollaboratorSampleId.equals(kitRequestShipping.getBspCollaboratorSampleId()));
        } else {
            // This should not happen, there is a known bug in KitUpload that causes this but this should be fixed
            // but if it does, log it and create an empty list
            log.info("No kit requests found for participant %s".formatted(ddpParticipantId));
            kitRequestShippings = new ArrayList<>();
        }
        KitRequestShipping newlyUpdatedKit = kitRequestDao.getKitRequestForCollaboratorSampleId(newCollaboratorSampleId);
        kitRequestShippings.add(newlyUpdatedKit);
        dsm.setKitRequestShipping(kitRequestShippings);
        ElasticSearchService.updateDsm(ddpParticipantId, dsm, esIndex);
    }

    /**
     * Collaborator sample id is unique in DSM, so we can use it to find a kit request, and to verify if we can use a
     * collaborator sample id to resample a kit. This method checks if a kit request with the given collaborator sample id
     * already exists in DSM, and if it belongs to the given participant.
     *
     * @param collaboratorSampleId collaborator sample id to check
     * @param ddpParticipantId participant id to check
     * @return true if a kit request with the given collaborator sample id exists in DSM and belongs to the given participant
     * */
    private static boolean existsKitRequestWithCollaboratorSampleId(String collaboratorSampleId, String ddpParticipantId,
                                                            KitRequestDao kitRequestDao) {
        KitRequestShipping kitRequestShipping = kitRequestDao.getKitRequestForCollaboratorSampleId(collaboratorSampleId);
        if (kitRequestShipping == null) {
            return false;
        }
        return StringUtils.isNotBlank(ddpParticipantId) ? ddpParticipantId.equals(kitRequestShipping.getDdpParticipantId()) : true;
    }
}
