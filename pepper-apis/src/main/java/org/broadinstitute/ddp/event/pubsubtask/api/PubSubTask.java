package org.broadinstitute.ddp.event.pubsubtask.api;

import java.util.Map;

/**
 * Data extracted from PubSubTask message
 * (published to pubsub topic which DSS subscribed to - subscription is specified by
 * config parameter "pubsub.pubSubTasksSubscription").
 *
 * <p>Data is fetched from pubsub message attributes and from message payload (JSON foc).
 */
public class PubSubTask {

    public static final String ATTR_TASK_TYPE = "taskType";

    private final String messageId;
    private final String taskType;
    private final Map<String, String> attributes;
    private final String payloadJson;


    /**
     * Put the data extracted from PubSubTask message
     * @param messageId - ID of a message
     * @param taskType - type of a task (known type is 'UPDATE_PROFILE'), it can be added new types/processors which
     *                 should be regsitered via {@link PubSubTaskProcessorFactory} and the factory implementation
     *                 to be passed as a parameter to {@link PubSubTaskConnectionService} constructor.
     * @param attributes  - pub sub message attributes
     * @param payloadJson - raw string with payload JSON doc.
     */
    public PubSubTask(String messageId, String taskType, Map<String, String> attributes, String payloadJson) {
        this.messageId = messageId;
        this.taskType = taskType;
        this.attributes = attributes;
        this.payloadJson = payloadJson;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getTaskType() {
        return taskType;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public String getPayloadJson() {
        return payloadJson;
    }
}
