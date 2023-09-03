package org.broadinstitute.dsm.service.admin;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.model.defaultvalues.RgpReferralSource;

@Slf4j
public class AdminOperationService {
    private final String userId;

    public AdminOperationService(String userId) {
        this.userId = userId;
    }

    public String startOperation(String operationId, Map<String, String> attributes, String payload) {
        OperationId opId;
        try {
            opId = OperationId.valueOf(operationId.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new DSMBadRequestException("Invalid operation ID: " + operationId);
        }
        if (!(opId.equals(OperationId.SYNC_REFERRAL_SOURCE))) {
            throw new DSMBadRequestException("Invalid operation ID: " + operationId);
        }

        // create operation record
        String jobId = "temp_job_id";

        // call service on a thread
        Thread t = new Thread(new RunOperation(opId, jobId, attributes, payload, userId));
        t.start();

        return jobId;
    }

    public String getOperationResults(String operationId) {
        return "not implemented";
    }

    public String getJobsResults(String jobId) {
        return "not implemented";
    }

    public static void updateJobRecord(int jobId, String status, String results) {

    }

    public enum OperationId {
        SYNC_REFERRAL_SOURCE
    }

    private static class RunOperation implements Runnable {
        private final OperationId operationId;
        private final String jobId;
        private final Map<String, String> attributes;
        private final String payload;
        private final String userId;

        public RunOperation(OperationId operationId, String jobId, Map<String, String> attributes, String payload, String userId) {
            this.operationId = operationId;
            this.jobId = jobId;
            this.attributes = attributes;
            this.payload = payload;
            this.userId = userId;
        }

        public void run() {
            try {
                switch (operationId) {
                    case SYNC_REFERRAL_SOURCE:
                        RgpReferralSource referralSource = new RgpReferralSource(userId);
                        referralSource.updateReferralSources(jobId);
                        break;
                    default:
                        log.error("RunOperation invalid operationId: {}", operationId);
                }
            } catch (Exception e) {
                log.error("Error operation with ID: {}", operationId, e);
            }
        }
    }
}
