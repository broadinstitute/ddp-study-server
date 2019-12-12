package org.broadinstitute.ddp.db.dto;

import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;

/**
 * An event that has been queued for dispatch
 * to pubsub.
 */
public class QueuedEventDto {
    private final String ddpEventId;
    private EventTriggerType triggerType;
    private String triggerStatus;
    private long secondsPostDelay;
    private long eventConfigurationId;
    private long queuedEventId;
    private String pubSubTopic;
    private String participantGuid;
    private String participantHruid;
    private EventActionType eventActionType;
    private String housekeepingVersion;
    private Integer maxOccurrencesPerUser;
    private String cancelCondition;
    private String precondition;
    private String studyGuid;

    /**
     * Instantiates QueuedEventDto object.
     */
    public QueuedEventDto(
            long eventConfigurationId,
            Long queuedEventId,
            String participantGuid,
            String participantHruid,
            EventActionType eventActionType,
            String housekeepingVersion,
            Integer maxOccurrencesPerUser,
            String pubSubTopic,
            String precondition,
            String cancelCondition,
            String studyGuid
    ) {
        this.eventConfigurationId = eventConfigurationId;
        this.queuedEventId = queuedEventId;
        this.participantGuid = participantGuid;
        this.participantHruid = participantHruid;
        this.eventActionType = eventActionType;
        this.housekeepingVersion = housekeepingVersion;
        this.maxOccurrencesPerUser = maxOccurrencesPerUser;
        this.pubSubTopic = pubSubTopic;
        ddpEventId = eventConfigurationId + "." + participantGuid;
        this.precondition = precondition;
        this.cancelCondition = cancelCondition;
        this.studyGuid = studyGuid;
    }

    public String getDdpEventId() {
        return ddpEventId;
    }

    public EventTriggerType getTriggerType() {
        return triggerType;
    }

    public String getTriggerStatus() {
        return triggerStatus;
    }

    public long getSecondsPostDelay() {
        return secondsPostDelay;
    }

    public long getEventConfigurationId() {
        return eventConfigurationId;
    }

    public EventActionType getActionType() {
        return eventActionType;
    }

    public Integer getMaxOccurrencesPerUser() {
        return maxOccurrencesPerUser;
    }

    public String getPubSubTopic() {
        return pubSubTopic;
    }

    public long getQueuedEventId() {
        return queuedEventId;
    }

    public String getCancelCondition() {
        return cancelCondition;
    }

    public String getParticipantGuid() {
        return participantGuid;
    }

    public String getParticipantHruid() {
        return participantHruid;
    }

    public String getPrecondition() {
        return precondition;
    }

    public String getStudyGuid() {
        return studyGuid;
    }

    public void setStudyGuid(String studyGuid) {
        this.studyGuid = studyGuid;
    }

    public String getHousekeepingVersion() {
        return housekeepingVersion;
    }
}
