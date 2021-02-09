package org.broadinstitute.ddp.event.pubsubtask.api;

import java.util.Map;

/**
 * Data extracted from PubSubTask message
 * (published to pubsub topic which DSS subscribed to - subscription isspecified by
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
    private final PayloadMap payloadMap;
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
     * @param payloadMap - if payload is set of name=value pairs then it is saved to this Map; if to convert to a Map -
     *                   is specified by a parameter 'payloadConvertibleToMap' in method
     *     {@link PubSubTaskProcessorFactoryAbstract#registerPubSubTaskProcessors(String, PubSubTaskProcessor, Class, boolean)}.
     * @param payloadObject - if payload class is not null, then contains parsed payload data;
     *                      payload class is specified by parameter 'payloadClass' in method
     *     {@link PubSubTaskProcessorFactoryAbstract#registerPubSubTaskProcessors(String, PubSubTaskProcessor, Class, boolean)}.
     */
    public PubSubTask(String messageId, String taskType, String participantGuid, String userId, String studyGuid,
                      String payloadJson, PayloadMap payloadMap, Object payloadObject) {
        this.messageId = messageId;
        this.taskType = taskType;
        this.participantGuid = participantGuid;
        this.userId = userId;
        this.studyGuid = studyGuid;
        this.payloadJson = payloadJson;
        this.payloadMap = payloadMap;
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

    public PayloadMap getPayloadMap() {
        return payloadMap;
    }

    public Object getPayloadObject() {
        return payloadObject;
    }


    public static class PayloadMap {
        private Map<String, String> map;

        public Map<String, String> getMap() {
            return map;
        }

        public void setMap(Map<String, String> map) {
            this.map = map;
        }
    }
}
