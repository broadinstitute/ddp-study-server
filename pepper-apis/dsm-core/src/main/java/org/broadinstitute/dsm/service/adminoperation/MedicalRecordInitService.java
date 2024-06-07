package org.broadinstitute.dsm.service.adminoperation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.service.admin.AdminOperationRecord;
import org.broadinstitute.dsm.service.medicalrecord.MedicalRecordService;
import org.broadinstitute.dsm.service.participant.OsteoParticipantService;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
import org.broadinstitute.lddp.handlers.util.InstitutionRequest;

@Slf4j
public class MedicalRecordInitService extends ParticipantAdminOperationService {
    protected List<String> validRealms =
            List.of(OsteoParticipantService.OSTEO1_INSTANCE_NAME.toLowerCase(),
                    OsteoParticipantService.OSTEO2_INSTANCE_NAME.toLowerCase(),
                    "cmi-lms");
    private DDPInstance ddpInstance;
    private InstitutionListRequest institutionListRequest;

    /**
     * Validate input and retrieve participant data, during synchronous part of operation handling
     *
     * @param userId     ID of user performing operation
     * @param realm      realm for fixup
     * @param attributes unused
     * @param payload    request body, if any, as ParticipantDataFixupRequest
     */
    public void initialize(String userId, String realm, Map<String, String> attributes, String payload) {
        ddpInstance = getDDPInstance(realm, validRealms);

        if (StringUtils.isBlank(payload)) {
            throw new DSMBadRequestException("Missing required payload");
        }

        // get and verify content of payload
        institutionListRequest = InstitutionListRequest.fromJson(payload);
        institutionListRequest.getInstitutionRequests().forEach(InstitutionRequest::verify);
    }

    /**
     * Run the asynchronous part of the operation, updating the AdminRecord with the operation results
     *
     * @param operationId ID for reporting results
     */
    public void run(int operationId) {
        List<UpdateLog> updateLog = new ArrayList<>();

        institutionListRequest.getInstitutionRequests().forEach(institutionRequest ->
                updateLog.add(writeMedicalRecordBundle(institutionRequest, ddpInstance)));

        // update job log record
        try {
            String json = ObjectMapperSingleton.writeValueAsString(updateLog);
            AdminOperationRecord.updateOperationRecord(operationId, AdminOperationRecord.OperationStatus.COMPLETED, json);
        } catch (Exception e) {
            log.error("Error writing operation log: {}", e.toString());
        }
    }

    protected static UpdateLog writeMedicalRecordBundle(InstitutionRequest institutionRequest, DDPInstance ddpInstance) {
        String ddpParticipantId = institutionRequest.getParticipantId();
        try {
            MedicalRecordService.writeInstitutionBundle(institutionRequest, ddpInstance);
            return new UpdateLog(ddpParticipantId, UpdateLog.UpdateStatus.UPDATED);
        } catch (Exception e) {
            String msg = "Error writing medical record bundle for participant %s: %s".formatted(ddpParticipantId, e);
            // many of these exceptions will require investigation, but conservatively we will just log
            // at error level for those that are definitely concerning
            if (e instanceof DsmInternalError) {
                log.error(msg);
                e.printStackTrace();
            } else {
                log.warn(msg);
            }
            return new UpdateLog(ddpParticipantId, UpdateLog.UpdateStatus.ERROR, e.toString());
        }
    }
}
