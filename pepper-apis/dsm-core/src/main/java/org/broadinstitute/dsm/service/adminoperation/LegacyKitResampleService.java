package org.broadinstitute.dsm.service.adminoperation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.ddp.kitrequest.KitRequestDao;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.ESMissingParticipantDataException;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.service.admin.AdminOperationRecord;
import org.broadinstitute.dsm.service.elastic.ElasticSearchService;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

@Slf4j
@Data
/**
 * Admin Operation to resample kits for participants to their legacy short IDs
 */
public class LegacyKitResampleService extends ParticipantAdminOperationService {
    private DDPInstance ddpInstance;
    private LegacyKitResampleList legacyKitResampleList;
    private KitRequestDao kitRequestDao = new KitRequestDao();
    private ElasticSearchService elasticSearchService = new ElasticSearchService();

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
        ddpInstance = DDPInstance.getDDPInstance(realm);

        if (StringUtils.isBlank(payload)) {
            throw new DSMBadRequestException("Missing required payload");
        }

        // get and verify content of payload
        legacyKitResampleList = LegacyKitResampleList.fromJson(payload);
        legacyKitResampleList.getResampleRequestList().forEach(legacyKitResampleRequest -> legacyKitResampleRequest.verify(ddpInstance,
                kitRequestDao));
    }

    /**
     * Run operation, typically asynchronously
     *
     * @param operationId ID for reporting results
     */
    @Override
    public void run(int operationId) {
        List<UpdateLog> updateLog = new ArrayList<>();

        legacyKitResampleList.getResampleRequestList().forEach(legacyKitResampleRequest ->
                updateLog.add(resampleKit(legacyKitResampleRequest, ddpInstance)));

        // update job log record
        try {
            String json = ObjectMapperSingleton.writeValueAsString(updateLog);
            AdminOperationRecord.updateOperationRecord(operationId, AdminOperationRecord.OperationStatus.COMPLETED, json);
        } catch (Exception e) {
            log.error("Error writing operation log: {}", e.toString());
        }
    }

    private UpdateLog resampleKit(LegacyKitResampleRequest legacyKitResampleRequest, DDPInstance ddpInstance) {
        Map<String, Object> participantProfile = ElasticSearchUtil.getParticipantProfileByShortID(ddpInstance,
                legacyKitResampleRequest.getShortId());
        String ddpParticipantId = participantProfile.get("guid").toString();
        String legacyParticipantId = participantProfile.get("legacyAltPid").toString();
        kitRequestDao.resampleKit(legacyKitResampleRequest, legacyParticipantId);
        changeDataInEs(legacyKitResampleRequest.getCurrentCollaboratorSampleId(), legacyKitResampleRequest.getNewCollaboratorSampleId(),
                 ddpParticipantId, ddpInstance.getParticipantIndexES());
        return new UpdateLog(ddpParticipantId, UpdateLog.UpdateStatus.ES_UPDATED,
                "Kit was resampled to " + legacyKitResampleRequest.getNewCollaboratorSampleId());
    }

    private void changeDataInEs(String currentCollaboratorSampleId, String newCollaboratorSampleId,
                                String ddpParticipantId, String esIndex) {
        Optional<ElasticSearchParticipantDto> maybeEsParticipant =
                elasticSearchService.getParticipantDocument(ddpParticipantId, esIndex);
        if (maybeEsParticipant.isEmpty()) {
            throw new ESMissingParticipantDataException("Participant %s does not have an ES document"
                    .formatted(ddpParticipantId));
        }
        ElasticSearchParticipantDto esParticipant = maybeEsParticipant.get();
        Dsm dsm = new Dsm();
        if (esParticipant.getDsm().isEmpty() || esParticipant.getActivities().isEmpty()) {
            log.info(String.format("Participant %s does not yet have DSM data and "
                    + "activities in ES", ddpParticipantId));

        } else {
            dsm = esParticipant.getDsm().get();
        }
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

    private List<KitRequestShipping> addNewKitRequestShippingToESKitsList(String newCollaboratorSampleId,
                                                                          List<KitRequestShipping> oldKits) {
        List<KitRequestShipping> kitRequestShippings = new ArrayList<>();
        oldKits.forEach(kitRequestShipping -> kitRequestShippings.add(kitRequestShipping));
        KitRequestShipping kitRequestShipping = kitRequestDao.getKitRequestWithCollaboratorSampleId(newCollaboratorSampleId);
        kitRequestShippings.add(kitRequestShipping);
        return kitRequestShippings;
    }
}
