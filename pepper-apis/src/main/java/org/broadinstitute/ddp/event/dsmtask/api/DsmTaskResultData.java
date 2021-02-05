package org.broadinstitute.ddp.event.dsmtask.api;

/**
 * Data which put to pubsub message published by DSS to pubsub topic "dss-to-dsm-results".
 */
public class DsmTaskResultData {

    public static final String ATTR_TASK_MESSAGE_ID = "taskMessageId";

    public enum DsmTaskResultType {
        SUCCESS,
        ERROR
    }

    private final boolean needsToRetry;

    private final DsmTaskResultPayload dsmTaskResultPayload;

    private final DsmTaskData dsmTaskData;


    public DsmTaskResultData(DsmTaskResultType resultType, DsmTaskData dsmTaskData) {
        this(resultType, null, dsmTaskData, false);
    }

    public DsmTaskResultData(DsmTaskResultType resultType, String errorMessage, DsmTaskData dsmTaskData, boolean needsToRetry) {
        this.dsmTaskResultPayload = new DsmTaskResultPayload(resultType, errorMessage);
        this.dsmTaskData = dsmTaskData;
        this.needsToRetry = needsToRetry;
    }

    public DsmTaskResultPayload getDsmTaskResultPayload() {
        return dsmTaskResultPayload;
    }

    public DsmTaskData getDsmTaskData() {
        return dsmTaskData;
    }

    public boolean isNeedsToRetry() {
        return needsToRetry;
    }

    public String toString() {
        return dsmTaskResultPayload.resultType + (dsmTaskResultPayload.errorMessage != null
                ? " [" + dsmTaskResultPayload.errorMessage + "]" : "");
    }


    public static class DsmTaskResultPayload {

        private final DsmTaskResultType resultType;
        private final String errorMessage;

        public DsmTaskResultPayload(DsmTaskResultType resultType, String errorMessage) {
            this.resultType = resultType;
            this.errorMessage = errorMessage;
        }

        public DsmTaskResultType getResultType() {
            return resultType;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
