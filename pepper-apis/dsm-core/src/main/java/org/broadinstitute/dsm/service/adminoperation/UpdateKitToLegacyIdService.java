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
import org.broadinstitute.dsm.service.admin.AdminOperationRecord;
import org.broadinstitute.dsm.service.elastic.ElasticSearchService;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

@Slf4j
/**
 * Admin Operation to change kits collaborator ids and ddpParticipantI to participant's legacy IDs
 */
public class UpdateKitToLegacyIdService extends ParticipantAdminOperationService {
    private static DDPInstanceDto ddpInstanceDto;
    private LegacyKitUpdateCollabIdList legacyKitUpdateCollabIdList;
    private static KitRequestDao kitRequestDao = new KitRequestDao();
    private DDPInstanceDao ddpInstanceDao = new DDPInstanceDao();
    private static ElasticSearchService elasticSearchService = new ElasticSearchService();

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
        legacyKitUpdateCollabIdList.getUpdateCollabIdRequests().forEach(legacyKitUpdateCollabIdRequest ->
                legacyKitUpdateCollabIdRequest.verify(ddpInstanceDto, kitRequestDao));
    }

    /**
     * Run operation, typically asynchronously
     *
     * @param operationId ID for reporting results
     */
    @Override
    public void run(int operationId) {
        List<UpdateLog> updateLog = new ArrayList<>();

        legacyKitUpdateCollabIdList.getUpdateCollabIdRequests().forEach(legacyKitUpdateCollabIdRequest ->
                updateLog.add(changeKitIdsToLegacyIds(legacyKitUpdateCollabIdRequest)));

        // update job log record
        try {
            String json = ObjectMapperSingleton.writeValueAsString(updateLog);
            AdminOperationRecord.updateOperationRecord(operationId, AdminOperationRecord.OperationStatus.COMPLETED, json);
        } catch (Exception e) {
            log.error("Error writing operation log: {}", e.toString());
        }
    }

    protected static UpdateLog changeKitIdsToLegacyIds(LegacyKitUpdateCollabIdRequest legacyKitUpdateCollabIdRequest) {
        try {
            Map<String, Object> participantProfile = ElasticSearchService.getParticipantProfileByShortID(
                    ddpInstanceDto.getInstanceName(), ddpInstanceDto.getEsParticipantIndex(), legacyKitUpdateCollabIdRequest.getShortId());
            String ddpParticipantId = participantProfile.get("guid").toString();
            String legacyParticipantId = participantProfile.get("legacyAltPid").toString();
            kitRequestDao.updateKitToLegacyIds(legacyKitUpdateCollabIdRequest, legacyParticipantId);
            changeDataInEs(legacyKitUpdateCollabIdRequest.getCurrentCollaboratorSampleId(),
                    legacyKitUpdateCollabIdRequest.getNewCollaboratorSampleId(), ddpParticipantId, ddpInstanceDto.getEsParticipantIndex());
            return new UpdateLog(ddpParticipantId, UpdateLog.UpdateStatus.ES_UPDATED,
                    "Kit collab id was changed from %s to %s".formatted(legacyKitUpdateCollabIdRequest.getCurrentCollaboratorSampleId(),
                            legacyKitUpdateCollabIdRequest.getNewCollaboratorSampleId()));
        } catch (Exception e) {
            return new UpdateLog(legacyKitUpdateCollabIdRequest.getShortId(), UpdateLog.UpdateStatus.ERROR,
                    "Error updating kit: " + legacyKitUpdateCollabIdRequest.currentCollaboratorSampleId + e.toString());
        }
    }

    private static void changeDataInEs(String currentCollaboratorSampleId, String newCollaboratorSampleId,
                                String ddpParticipantId, String esIndex) {
        Dsm dsm = elasticSearchService.getRequiredDsmData(ddpParticipantId, esIndex);
        List<KitRequestShipping> kitRequestShippings = dsm.getKitRequestShipping();
        if (kitRequestShippings != null) {
            kitRequestShippings.removeIf(
                    kitRequestShipping -> currentCollaboratorSampleId.equals(kitRequestShipping.getBspCollaboratorSampleId()));
        } else {
            kitRequestShippings = new ArrayList<>();
        }
        kitRequestShippings = addNewKitRequestShippingToESKitsList(newCollaboratorSampleId, kitRequestShippings);
        dsm.setKitRequestShipping(kitRequestShippings);
        ElasticSearchService.updateDsm(ddpParticipantId, dsm, esIndex);
    }

    private static List<KitRequestShipping> addNewKitRequestShippingToESKitsList(String newCollaboratorSampleId,
                                                                          List<KitRequestShipping> oldKits) {
        List<KitRequestShipping> kitRequestShippings = new ArrayList<>();
        oldKits.forEach(kitRequestShipping -> kitRequestShippings.add(kitRequestShipping));
        KitRequestShipping kitRequestShipping = kitRequestDao.getKitRequestForCollaboratorSampleId(newCollaboratorSampleId);
        kitRequestShippings.add(kitRequestShipping);
        return kitRequestShippings;
    }
}
