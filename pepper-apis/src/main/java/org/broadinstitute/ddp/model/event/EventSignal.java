package org.broadinstitute.ddp.model.event;

import org.broadinstitute.ddp.model.activity.types.EventTriggerType;

public class EventSignal {
    private final long operatorId;
    private final long participantId;
    private final long studyId;
    private final String studyGuid;
    private final String participantGuid;
    private final String operatorGuid;
    private final EventTriggerType eventTriggerType;

    public EventSignal(long operatorId,
                       long participantId,
                       String participantGuid,
                       String operatorGuid,
                       long studyId,
                       String studyGuid,
                       EventTriggerType eventTriggerType) {
        this.operatorGuid = operatorGuid;
        this.eventTriggerType = eventTriggerType;
        this.operatorId = operatorId;
        this.participantId = participantId;
        this.participantGuid = participantGuid;
        this.studyId = studyId;
        this.studyGuid = studyGuid;
    }

    public long getOperatorId() {
        return operatorId;
    }

    public long getParticipantId() {
        return participantId;
    }

    public String getParticipantGuid() {
        return participantGuid;
    }

    public String getOperatorGuid() {
        return operatorGuid;
    }

    public EventTriggerType getEventTriggerType() {
        return eventTriggerType;
    }

    public long getStudyId() {
        return studyId;
    }

    public String getStudyGuid() {
        return studyGuid;
    }

    @Override
    public String toString() {
        return "EventSignal{"
                + "triggerType=" + eventTriggerType
                + ", studyId=" + studyId
                + ", operatorId=" + operatorId
                + ", participantId=" + participantId
                + '}';
    }
}
