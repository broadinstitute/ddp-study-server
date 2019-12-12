package org.broadinstitute.ddp.model.event;

import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;

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

    public InstanceStatusType getInstanceStatusType() {
        return instanceStatusType;
    }
}
