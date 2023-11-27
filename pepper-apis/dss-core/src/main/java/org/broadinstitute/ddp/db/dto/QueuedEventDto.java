package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Data
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class QueuedEventDto {
    @ColumnName("queued_event_id")
    private final long queuedEventId;

    @ColumnName("operator_id")
    private final Long operatorUserId;

    @ColumnName("participant_guid")
    private final String participantGuid;

    @ColumnName("participant_hruid")
    private final String participantHruid;

    @ColumnName("operator_guid")
    private final String operatorGuid;

    @ColumnName("event_configuration_id")
    private final long eventConfigurationId;

    @ColumnName("event_trigger_type")
    private final EventTriggerType triggerType;

    @ColumnName("event_action_type")
    private final EventActionType actionType;

    @ColumnName("post_delay_seconds")
    private final Long postDelaySeconds;

    @ColumnName("max_occurrences_per_user")
    private final Integer maxOccurrencesPerUser;

    @ColumnName("gcp_topic")
    private final String pubSubTopic;

    @ColumnName("precondition")
    private final String precondition;

    @ColumnName("cancel_condition")
    private final String cancelCondition;

    @ColumnName("study_guid")
    private final String studyGuid;

    public QueuedEventDto(final QueuedEventDto other) {
        this(other.getQueuedEventId(),
                other.getOperatorUserId(),
                other.getParticipantGuid(),
                other.getParticipantHruid(),
                other.getOperatorGuid(),
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

    public String getDdpEventId() {
        return eventConfigurationId + "." + participantGuid;
    }
}
