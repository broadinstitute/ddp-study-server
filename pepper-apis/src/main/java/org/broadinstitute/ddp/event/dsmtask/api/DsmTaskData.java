package org.broadinstitute.ddp.event.dsmtask.api;

public class DsmTaskData {

    public static final String ATTR_TASK_TYPE = "taskType";
    public static final String ATTR_PARTICIPANT_GUID = "participantGuid";
    public static final String ATTR_USER_ID = "userId";
    public static final String ATTR_STUDY_GUID = "studyGuid";

    private final String messageId;
    private final String taskType;
    private final String participantGuid;
    private final String userId;
    private final String studyGuid;
    private final String payload;
    private final Object payloadObject;

    public DsmTaskData(String messageId, String taskType, String participantGuid, String userId, String studyGuid) {
        this(messageId, taskType, participantGuid, userId, studyGuid, null, null);
    }

    public DsmTaskData(String messageId, String taskType, String participantGuid, String userId, String studyGuid,
                       String payload, Object payloadObject) {
        this.messageId = messageId;
        this.taskType = taskType;
        this.participantGuid = participantGuid;
        this.userId = userId;
        this.studyGuid = studyGuid;
        this.payload = payload;
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

    public String getPayload() {
        return payload;
    }

    public Object getPayloadObject() {
        return payloadObject;
    }
}
