package org.broadinstitute.dsm.service.adminoperation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.ddp.kitrequest.KitRequestDao;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.service.admin.AdminOperationRecord;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

@Slf4j
public class LegacyKitResampleService extends ParticipantAdminOperationService {
    private DDPInstance ddpInstance;
    private LegacyKitResampleList legacyKitResampleList;
    private KitRequestDao kitRequestDao = new KitRequestDao();

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
        String ddpParticipantId = participantProfile.get(DBConstants.DDP_PARTICIPANT_ID).toString();
        String legacyParticipantId = participantProfile.get("legacyAltPid").toString();
        kitRequestDao.resampleKit(legacyKitResampleRequest, legacyParticipantId);
        Map<String, Object> participantDsm = ElasticSearchUtil.getDsmForSingleParticipantFromES(ddpInstance.getName(),
                ddpInstance.getParticipantIndexES(), legacyKitResampleRequest.getShortId());
        changeDataInEs(legacyKitResampleRequest.getCurrentCollaboratorSampleId(), legacyKitResampleRequest.getNewCollaboratorSampleId(),
                participantDsm, ddpParticipantId, ddpInstance.getParticipantIndexES());
        return new UpdateLog(ddpParticipantId, UpdateLog.UpdateStatus.ES_UPDATED,
                "Kit was resampled to " + legacyKitResampleRequest.getNewCollaboratorSampleId());
    }

    private void changeDataInEs(String currentCollaboratorSampleId, String newCollaboratorSampleId, Map<String, Object>
            participantDsm, String ddpParticipantId, String index) {
        List<Map<String, Object>> kitRequests = (List<Map<String, Object>>) participantDsm.get("kitRequestShipping");
        kitRequests.removeIf(stringObjectMap ->  currentCollaboratorSampleId.equals(stringObjectMap.get("bspCollaboratorSampleId")));
        kitRequests.add(addNewKitRequestShippingToES(participantDsm, newCollaboratorSampleId));
        participantDsm.put("kitRequestShipping", kitRequests);
        ElasticSearchUtil.updateRequest(ddpParticipantId, index, participantDsm);
    }

    private Map<String, Object> addNewKitRequestShippingToES(Map<String, Object> participantDsm, String newCollaboratorSampleId) {
        KitRequestShipping kitRequestShipping = kitRequestDao.getKitRequestWithCollaboratorSampleId(newCollaboratorSampleId);
        return new ObjectMapper().convertValue(kitRequestShipping, Map.class);
    }
}
