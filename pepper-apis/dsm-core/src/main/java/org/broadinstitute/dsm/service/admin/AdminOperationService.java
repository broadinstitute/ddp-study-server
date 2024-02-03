package org.broadinstitute.dsm.service.admin;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.service.adminoperation.ParticipantDataFixupService;
import org.broadinstitute.dsm.service.adminoperation.ReferralSourceService;

/**
 * Provides a service to run admin operations (e.g. fixing data, etc.) typically asynchronously, recording results
 * in the DB for later reference
 */
@Slf4j
public class AdminOperationService {

    // supported operations
    public enum OperationTypeId {
        SYNC_REFERRAL_SOURCE,
        FIXUP_PARTICIPANT_DATA
    }

    private final String userId;
    private final String realm;

    public AdminOperationService(String userId, String realm) {
        this.userId = userId;
        this.realm = realm;
    }

    public String startOperation(String operationTypeId, Map<String, String> attributes, String payload) {
        OperationTypeId opId = validateOperationTypeId(operationTypeId);

        // trivial for now... this will get expanded as we add more operations
        AdminOperation adminOperation;
        switch (opId) {
            case SYNC_REFERRAL_SOURCE:
                adminOperation = new ReferralSourceService();
                break;
            case FIXUP_PARTICIPANT_DATA:
                adminOperation = new ParticipantDataFixupService();
                break;
            default:
                throw new DSMBadRequestException("Invalid operation type ID: " + operationTypeId);
        }
        adminOperation.initialize(userId, realm, attributes, payload);

        // create operation record
        int operationId = AdminOperationRecord.createOperationRecord(opId, userId);

        // call service on a thread
        Thread t = new Thread(new RunOperation(adminOperation, opId, operationId));
        t.start();

        return Integer.toString(operationId);
    }

    private OperationTypeId validateOperationTypeId(String operationType) {
        try {
            return OperationTypeId.valueOf(operationType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new DSMBadRequestException("Invalid operation type ID: " + operationType);
        }
    }

    public AdminOperationResponse getOperationResults(String operationId) {
        try {
            AdminOperationResponse.AdminOperationResult result = AdminOperationRecord.getOperationRecord(Integer.parseInt(operationId));
            if (result == null) {
                throw new DSMBadRequestException("Operation ID does not match any operations: " + operationId);
            }
            return new AdminOperationResponse(result);
        } catch (NumberFormatException e) {
            throw new DSMBadRequestException("Invalid operation ID format (expecting integer): " + operationId);
        }
    }

    public AdminOperationResponse getOperationTypeResults(String operationTypeId) {
        OperationTypeId opId = validateOperationTypeId(operationTypeId);
        List<AdminOperationResponse.AdminOperationResult> results = AdminOperationRecord.getOperationTypeRecords(opId.name());
        if (results.isEmpty()) {
            throw new DSMBadRequestException("Operation type ID does not match any operations: " + operationTypeId);
        }
        return new AdminOperationResponse(results);
    }


    private static class RunOperation implements Runnable {
        private final AdminOperation adminOperation;
        private final OperationTypeId operationTypeId;
        private final int operationId;

        public RunOperation(AdminOperation adminOperation, OperationTypeId operationTypeId, int operationId) {
            this.adminOperation = adminOperation;
            this.operationTypeId = operationTypeId;
            this.operationId = operationId;
        }

        public void run() {
            try {
                adminOperation.run(operationId);
            } catch (Exception e) {
                log.error("Error running operation with type: {}", operationTypeId, e);
            }
        }
    }
}
