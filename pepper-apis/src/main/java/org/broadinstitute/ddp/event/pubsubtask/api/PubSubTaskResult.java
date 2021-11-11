package org.broadinstitute.ddp.event.pubsubtask.api;

import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask.ATTR_NAME__TASK_TYPE;
import static org.broadinstitute.ddp.util.GsonCreateUtil.createJsonIgnoreNulls;

import java.util.HashMap;
import java.util.Map;

/**
 * Data to be published to PubSubTaskResult topic.
 */
public class PubSubTaskResult {

    /**
     * Name of an attribute to be added to result message.
     * It contains ID of a PubSubTask-message (incoming message)
     */
    public static final String ATTR_TASK__MESSAGE_ID = "taskMessageId";

    public static final String PAYLOAD_FIELD__RESULT_TYPE = "resultType";
    public static final String PAYLOAD_FIELD__ERROR_MESSAGE = "errorMessage";

    public enum PubSubTaskResultType {
        SUCCESS,
        ERROR
    }

    private final PubSubTaskResultType resultType;
    private final String errorMessage;

    private final PubSubTask pubSubTask;

    private final Map<String, String> attributes = new HashMap<>();

    private String payloadJson;

    public PubSubTaskResult(PubSubTaskResultType resultType, String errorMessage, PubSubTask pubSubTask) {
        this(resultType, errorMessage, pubSubTask, null);
    }

    public PubSubTaskResult(PubSubTaskResultType resultType, String errorMessage, PubSubTask pubSubTask, String payloadJson) {
        this.resultType = resultType;
        this.errorMessage = errorMessage;
        this.pubSubTask = pubSubTask;
        if (pubSubTask != null) {
            this.attributes.putAll(pubSubTask.getAttributes());
            this.attributes.put(ATTR_TASK__MESSAGE_ID, pubSubTask.getMessageId());
            this.attributes.put(ATTR_NAME__TASK_TYPE, pubSubTask.getTaskType());
        }
        this.payloadJson = payloadJson != null ? payloadJson : generateDefaultPayload(this);
    }

    public PubSubTaskResultType getResultType() {
        return resultType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public PubSubTask getPubSubTask() {
        return pubSubTask;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    @Override
    public String toString() {
        return resultType + (errorMessage != null ? " : " + errorMessage : "")
                + (payloadJson != null ? ": [" + payloadJson : "]");
    }

    private static String generateDefaultPayload(PubSubTaskResult pubSubTaskResult) {
        return createJsonIgnoreNulls(
                PAYLOAD_FIELD__RESULT_TYPE, pubSubTaskResult.resultType,
                PAYLOAD_FIELD__ERROR_MESSAGE, pubSubTaskResult.errorMessage);
    }
}
