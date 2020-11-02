package org.broadinstitute.ddp.model.event;

import org.broadinstitute.ddp.model.activity.types.EventTriggerType;

public class EventSignal {
    private long operatorId;
    private long participantId;
    private long studyId;
    private String participantGuid;
    private String operatorGuid;
    private EventTriggerType eventTriggerType;

    public EventSignal(long operatorId,
                       long participantId,
                       String participantGuid,
                       String operatorGuid, long studyId,
                       EventTriggerType eventTriggerType) {
        this.operatorGuid = operatorGuid;
        this.eventTriggerType = eventTriggerType;
        this.operatorId = operatorId;
        this.participantId = participantId;
        this.participantGuid = participantGuid;
        this.studyId = studyId;
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
