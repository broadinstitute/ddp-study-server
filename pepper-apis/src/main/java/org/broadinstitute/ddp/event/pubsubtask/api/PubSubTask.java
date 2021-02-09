package org.broadinstitute.ddp.event.pubsubtask.api;

/**
 * Data extracted from PubSubTask message
 * (published to pubsub topic which DSS subscribed to - subscription is specified by
 * config parameter "pubsub.pubSubTasksSubscription").
 *
 * <p>Data is fetched from pubsub message attributes and from message payload (JSON foc).
 */
public class PubSubTask {

    public static final String ATTR_TASK_TYPE = "taskType";
    public static final String ATTR_PARTICIPANT_GUID = "participantGuid";
    public static final String ATTR_USER_ID = "userId";
    public static final String ATTR_STUDY_GUID = "studyGuid";

    private final String messageId;
    private final String taskType;
    private final String participantGuid;
    private final String userId;
    private final String studyGuid;

    private final String payloadJson;
    private final Object payloadObject;


    /**
     * Put the data extracted from PubSubTask message
     * @param messageId - ID of a message
     * @param taskType - type of a task (known type is 'UPDATE_PROFILE'), it can be added new types/processors which
     *                 should be regsitered via {@link PubSubTaskProcessorFactory} and the factory implementation
     *                 to be passed as a parameter to {@link PubSubTaskConnectionService} constructor.
     * @param participantGuid - GUID of a user (equivalent to DB user.guid).
     * @param userId - ID of a user (it's not used on DSS side but just needs to be returned in {@link PubSubTaskResult}
     *               (so, it's a userID known on client side).
     * @param studyGuid - study GUID (for example 'Osteo').
     * @param payloadJson - raw string with payload JSON doc.
     * @param payloadObject - if payload class is not null, then contains parsed payload data;
     *                      payload class is specified by parameter 'payloadClass' in method
     *     {@link PubSubTaskProcessorFactoryAbstract#registerPubSubTaskProcessors(String, PubSubTaskProcessor, Class)}.
     */
    public PubSubTask(String messageId, String taskType, String participantGuid, String userId, String studyGuid,
                      String payloadJson, Object payloadObject) {
        this.messageId = messageId;
        this.taskType = taskType;
        this.participantGuid = participantGuid;
        this.userId = userId;
        this.studyGuid = studyGuid;
        this.payloadJson = payloadJson;
        this.payloadObject = payloadObject;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getTaskType() {
        return taskType;
    }

    public String getParticipantGuid() {
        return participantGuid;
    }

    public String getUserId() {
        return userId;
    }

    public String getStudyGuid() {
        return studyGuid;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public Object getPayloadObject() {
        return payloadObject;
    }
}
