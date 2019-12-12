package org.broadinstitute.ddp.db.dto;

import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;

public class ActivityInstanceStatusChangeDto {
    private long updatedAtEpochMillis;
    private long activityInstanceId;
    private InstanceStatusType activityInstanceStatusType;

    public ActivityInstanceStatusChangeDto(long updatedAtEpochMillis, long activityInstanceId,
                                           InstanceStatusType activityInstanceStatusType) {
        this.updatedAtEpochMillis = updatedAtEpochMillis;
        this.activityInstanceId = activityInstanceId;
        this.activityInstanceStatusType = activityInstanceStatusType;
    }

    public long getUpdatedAtEpochMillis() {
        return updatedAtEpochMillis;
    }

    public long getActivityInstanceId() {
        return activityInstanceId;
    }

    public InstanceStatusType getActivityInstanceStatusType() {
        return activityInstanceStatusType;
    }
}
