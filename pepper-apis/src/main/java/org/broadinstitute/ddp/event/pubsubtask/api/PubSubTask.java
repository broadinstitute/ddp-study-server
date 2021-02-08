package org.broadinstitute.ddp.event.pubsubtask.api;

import java.util.Map;

/**
 * Data extracted from PubSubTask message
 * (published to incoming subscription specified by config parameter ""pubsub.pubSubTasksSubscription").
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
