package org.broadinstitute.ddp.db.dto;

import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

/**
 * An event that has been queued for dispatch to pubsub.
 */
public class QueuedEventDto {

    private final String housekeepingVersion = "1.0";
    private final String ddpEventId;
    private long queuedEventId;
    private Long operatorUserId;
    private String participantGuid;
    private String participantHruid;
    private long eventConfigurationId;
    private EventTriggerType triggerType;
    private EventActionType actionType;
    private Long postDelaySeconds;
    private Integer maxOccurrencesPerUser;
    private String pubSubTopic;
    private String precondition;
    private String cancelCondition;
    private String studyGuid;

    @JdbiConstructor
    public QueuedEventDto(
            @ColumnName("queued_event_id") long queuedEventId,
            @ColumnName("operator_id") Long operatorUserId,
            @ColumnName("participant_guid") String participantGuid,
            @ColumnName("participant_hruid") String participantHruid,
            @ColumnName("event_configuration_id") long eventConfigurationId,
            @ColumnName("event_trigger_type") EventTriggerType triggerType,
            @ColumnName("event_action_type") EventActionType actionType,
            @ColumnName("post_delay_seconds") Long postDelaySeconds,
            @ColumnName("max_occurrences_per_user") Integer maxOccurrencesPerUser,
            @ColumnName("gcp_topic") String pubSubTopic,
            @ColumnName("precondition") String precondition,
            @ColumnName("cancel_condition") String cancelCondition,
            @ColumnName("study_guid") String studyGuid) {
        ddpEventId = eventConfigurationId + "." + participantGuid;
        this.queuedEventId = queuedEventId;
        this.operatorUserId = operatorUserId;
        this.participantGuid = participantGuid;
        this.participantHruid = participantHruid;
        this.eventConfigurationId = eventConfigurationId;
        this.triggerType = triggerType;
        this.actionType = actionType;
        this.postDelaySeconds = postDelaySeconds;
        this.maxOccurrencesPerUser = maxOccurrencesPerUser;
        this.pubSubTopic = pubSubTopic;
        this.precondition = precondition;
        this.cancelCondition = cancelCondition;
        this.studyGuid = studyGuid;
    }

    public QueuedEventDto(QueuedEventDto other) {
        this(other.getQueuedEventId(),
                other.getOperatorUserId(),
                other.getParticipantGuid(),
                other.getParticipantHruid(),
                other.getEventConfigurationId(),
                other.getTriggerType(),
                other.getActionType(),
                other.getPostDelaySeconds(),
                other.getMaxOccurrencesPerUser(),
                other.getPubSubTopic(),
                other.getPrecondition(),
                other.getCancelCondition(),
                other.getStudyGuid());
    }

    public String getHousekeepingVersion() {
        return housekeepingVersion;
    }

    public String getDdpEventId() {
        return ddpEventId;
    }

    public long getQueuedEventId() {
        return queuedEventId;
    }

    public Long getOperatorUserId() {
        return operatorUserId;
    }

    public String getParticipantGuid() {
        return participantGuid;
    }

    public String getParticipantHruid() {
        return participantHruid;
    }

    public long getEventConfigurationId() {
        return eventConfigurationId;
    }

    public EventTriggerType getTriggerType() {
        return triggerType;
    }

    public EventActionType getActionType() {
        return actionType;
    }

    public Long getPostDelaySeconds() {
        return postDelaySeconds;
    }

    public Integer getMaxOccurrencesPerUser() {
        return maxOccurrencesPerUser;
    }

    public String getPubSubTopic() {
        return pubSubTopic;
    }

    public String getPrecondition() {
        return precondition;
    }

    public String getCancelCondition() {
        return cancelCondition;
    }

    public String getStudyGuid() {
        return studyGuid;
    }
}
