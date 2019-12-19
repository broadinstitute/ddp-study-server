package org.broadinstitute.ddp.model.event;

import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.jdbi.v3.core.Handle;

public class ActivityStatusChangeTrigger extends EventTrigger {
    private long activityInstanceId;
    private InstanceStatusType instanceStatusType;

    public ActivityStatusChangeTrigger(EventConfigurationDto dto) {
        super(dto);
        this.activityInstanceId = dto.getActivityStatusTriggerStudyActivityId();
        this.instanceStatusType = dto.getInstanceStatusType();
    }

    public Long getActivityInstanceId() {
        return activityInstanceId;
    }

    @Override
    public boolean isTriggered(Handle handle, EventSignal eventSignal) {
        if (eventSignal.getEventTriggerType() == EventTriggerType.ACTIVITY_STATUS) {
            ActivityInstanceStatusChangeSignal signal = (ActivityInstanceStatusChangeSignal) eventSignal;
            return signal.getActivityIdThatChanged() == activityInstanceId
                    && instanceStatusType == signal.getTargetStatusType();
        } else {
            return true;
        }
    }

    public InstanceStatusType getInstanceStatusType() {
        return instanceStatusType;
    }
}
