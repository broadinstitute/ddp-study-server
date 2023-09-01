package org.broadinstitute.dsm.service.admin;

import java.util.Map;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.pubsub.WorkflowStatusUpdate;

@Slf4j
public class AdminOperationService {
    private final String userId;

    public AdminOperationService(String userId) {
        this.userId = userId;
    }

    public String startOperation(String operationId, Map<String, String> attributes, String payload) {
        if (!operationId.equals(OperationId.SYNC_REFERRAL_SOURCE.name())) {
            throw new DSMBadRequestException("Invalid operation ID: " + operationId);
        }

        // create operation record

        // call service on a thread

    }

    public String getOperationResults(String operationId) {

    }

    public String getJobsResults(String jobId) {

    }

    public static void updateJobRecord(int jobId, String status, String results) {

    }

    public enum OperationId {
        SYNC_REFERRAL_SOURCE
    }
}
