package org.broadinstitute.ddp.event.pubsubtask.api;

import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask.ATTR_TASK_TYPE;
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
    public static final String ATTR_TASK_MESSAGE_ID = "taskMessageId";

    public static final String FIELD_RESULT_TYPE = "resultType";
    public static final String FIELD_ERROR_MESSAGE = "errorMessage";

    public enum PubSubTaskResultType {
        SUCCESS,
        ERROR
    }

    private final PubSubTaskResultType resultType;
    private final String errorMessage;

    private final PubSubTask pubSubTask;

    private final Map<String, String> attributes = new HashMap<>();

    private String jsonPayload;

    public PubSubTaskResult(PubSubTaskResultType resultType, String errorMessage, PubSubTask pubSubTask) {
        this.resultType = resultType;
        this.errorMessage = errorMessage;
        this.pubSubTask = pubSubTask;
        if (pubSubTask != null) {
            this.attributes.putAll(pubSubTask.getAttributes());
            this.attributes.put(ATTR_TASK_MESSAGE_ID, pubSubTask.getMessageId());
            this.attributes.put(ATTR_TASK_TYPE, pubSubTask.getTaskType());
        }
        this.jsonPayload = generateDefaultPayload(this);
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

    public String getJsonPayload() {
        return jsonPayload;
    }

    public void setJsonPayload(String jsonPayload) {
        this.jsonPayload = jsonPayload;
    }

    @Override
    public String toString() {
        return resultType + (errorMessage != null ? ": " + errorMessage : "");
    }

    private static String generateDefaultPayload(PubSubTaskResult pubSubTaskResult) {
        return createJsonIgnoreNulls(
                FIELD_RESULT_TYPE, pubSubTaskResult.resultType,
                FIELD_ERROR_MESSAGE, pubSubTaskResult.errorMessage);
    }
}
