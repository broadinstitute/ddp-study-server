package org.broadinstitute.dsm.service.admin;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.model.defaultvalues.ReferralSourceService;

/**
 * Provides a service to run admin operations (e.g. fixing data, etc.) typically asynchronously, recording results
 * in the DB for later reference
 */
@Slf4j
public class AdminOperationService {

    // supported operations
    public enum OperationTypeId {
        SYNC_REFERRAL_SOURCE
    }

    private final String userId;
    private final String realm;

    public AdminOperationService(String userId, String realm) {
        this.userId = userId;
        this.realm = realm;
    }

    public String startOperation(String operationTypeId, Map<String, String> attributes, String payload) {
        OperationTypeId opId;
        try {
            opId = OperationTypeId.valueOf(operationTypeId.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new DSMBadRequestException("Invalid operation type ID: " + operationTypeId);
        }

        // trivial for now... this will get expanded as we add more operations
        AdminOperation adminOperation;
        switch (opId) {
            case SYNC_REFERRAL_SOURCE:
                adminOperation = new ReferralSourceService();
                adminOperation.initialize(userId, realm, attributes, payload);
                break;
            default:
                throw new DSMBadRequestException("Invalid operation type ID: " + operationTypeId);
        }

        // create operation record
        int operationId = 42;

        // call service on a thread
        Thread t = new Thread(new RunOperation(adminOperation, opId, operationId));
        t.start();

        return Integer.toString(operationId);
    }

    public String getOperationResults(String operationId) {
        return "not implemented";
    }

    public String getOperationTypeResults(String jobId) {
        return "not implemented";
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
