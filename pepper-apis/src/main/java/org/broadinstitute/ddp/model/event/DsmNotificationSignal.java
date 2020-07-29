package org.broadinstitute.ddp.model.event;

import org.broadinstitute.ddp.model.activity.types.DsmNotificationEventType;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;

public class DsmNotificationSignal extends EventSignal {

    private DsmNotificationEventType eventType;

    public DsmNotificationSignal(long operatorId, long participantId, String participantGuid,
                                 long studyId, DsmNotificationEventType eventType) {
        super(operatorId, participantId, participantGuid, studyId, EventTriggerType.DSM_NOTIFICATION);
        this.eventType = eventType;
    }

    public DsmNotificationEventType getDsmEventType() {
        return eventType;
    }
}
