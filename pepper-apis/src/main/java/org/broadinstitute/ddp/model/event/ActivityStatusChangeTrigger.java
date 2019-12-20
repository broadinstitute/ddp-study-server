package org.broadinstitute.ddp.model.event;

import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.jdbi.v3.core.Handle;

public class ActivityStatusChangeTrigger extends EventTrigger {
    private long studyActivityId;
    private InstanceStatusType instanceStatusType;

    public ActivityStatusChangeTrigger(EventConfigurationDto dto) {
        super(dto);
        this.studyActivityId = dto.getActivityStatusTriggerStudyActivityId();
        this.instanceStatusType = dto.getInstanceStatusType();
    }

    public Long getStudyActivityId() {
        return studyActivityId;
    }

    @Override
    public boolean isTriggered(Handle handle, EventSignal eventSignal) {
        if (eventSignal.getEventTriggerType() == EventTriggerType.ACTIVITY_STATUS) {
            ActivityInstanceStatusChangeSignal signal = (ActivityInstanceStatusChangeSignal) eventSignal;
            return signal.getActivityIdThatChanged() == studyActivityId
                    && instanceStatusType == signal.getTargetStatusType();
        } else {
            return true;
        }
    }

    public InstanceStatusType getInstanceStatusType() {
        return instanceStatusType;
    }

    @Override
    public String toString() {
        return "ActivityStatusChangeTrigger{"
                + "studyActivityId=" + studyActivityId
                + ", instanceStatusType=" + instanceStatusType + '}';
    }
}
