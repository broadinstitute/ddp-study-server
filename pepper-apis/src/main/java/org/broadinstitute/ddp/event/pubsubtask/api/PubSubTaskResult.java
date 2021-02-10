package org.broadinstitute.ddp.event.pubsubtask.api;

/**
 * Data to be published to PubSubTaskResult topic.
 */
public class PubSubTaskResult {

    /**
     * Name of an attribute to be added to result message.
     * It contains ID of a PubSubTask-message (incoming message)
     */
    public static final String ATTR_TASK_MESSAGE_ID = "taskMessageId";

    public enum PubSubTaskResultType {
        SUCCESS,
        ERROR
    }

    private final PubSubTaskResultPayload pubSubTaskResultPayload;

    private final PubSubTask pubSubTask;

    public PubSubTaskResult(PubSubTaskResultType resultType, String errorMessage, PubSubTask pubSubTask) {
        this.pubSubTaskResultPayload = new PubSubTaskResultPayload(resultType, errorMessage);
        this.pubSubTask = pubSubTask;
    }

    public PubSubTaskResultPayload getPubSubTaskResultPayload() {
        return pubSubTaskResultPayload;
    }

    public PubSubTask getPubSubTask() {
        return pubSubTask;
    }

    public String toString() {
        return pubSubTaskResultPayload.resultType + (pubSubTaskResultPayload.errorMessage != null
                ? " [" + pubSubTaskResultPayload.errorMessage + "]" : "");
    }


    /**
     * Data to be set to PubSubTaskResult payload (JSON document).
     * It contains the following fields:
     * <ul>
     *     <li>resultType - type of result (SUCCESS or ERROR);</li>
     *     <li>errorMessage - message (if resultType==ERROR).</li>
     * </ul>
     */
    public static class PubSubTaskResultPayload {

        private final PubSubTaskResultType resultType;
        private final String errorMessage;

        public PubSubTaskResultPayload(PubSubTaskResultType resultType, String errorMessage) {
            this.resultType = resultType;
            this.errorMessage = errorMessage;
        }

        public PubSubTaskResultType getResultType() {
            return resultType;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
