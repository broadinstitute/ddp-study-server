package org.broadinstitute.ddp.event.pubsubtask.api;

import java.util.Map;

/**
 * Data extracted from PubSubTask message
 * (published to pubsub topic which DSS subscribed to - subscription is specified by
 * config parameter "pubsub.pubSubTasksSubscription").
 *
 * <p>Data is fetched from pubsub message attributes and from message payload (JSON doc).
 */
public class PubSubTask {

    /** Name of a PubSub message attribute holding a PubSub task type (for example 'UPDATE_PROFILE', 'USER_DELETE') */
    public static final String ATTR_NAME__TASK_TYPE = "taskType";

    /** PubSubTask standard attribute: holds a study GUID */
    public static final String ATTR_NAME__STUDY_GUID = "studyGuid";

    /** PubSubTask standard attribute: holds a participant (user) GUID */
    public static final String ATTR_NAME__PARTICIPANT_GUID = "participantGuid";

    /** PubSubTask standard attribute: holds an operator  GUID */
    public static final String ATTR_NAME__OPERATOR_GUID = "operatorGuid";

    private final String messageId;
    private final String taskType;
    private final Map<String, String> attributes;
    private final String payloadJson;


    /**
     * Put the data extracted from PubSubTask message
     * @param messageId - ID of a message
     * @param taskType - type of a task: one of known types is 'UPDATE_PROFILE' and it can be added new types/processors which
     *                 should be registered via {@link PubSubTaskProcessorFactory} and the factory implementation
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

    @Override
    public String toString() {
        return "taskType=" + taskType + ", messageId=" + messageId + ", attr=" + attributes + ", payload=" + payloadJson;
    }
}
