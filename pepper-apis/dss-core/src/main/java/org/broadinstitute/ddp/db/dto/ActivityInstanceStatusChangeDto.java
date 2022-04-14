package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;

@Value
@AllArgsConstructor
public class ActivityInstanceStatusChangeDto {
    long updatedAtEpochMillis;
    long activityInstanceId;
    InstanceStatusType activityInstanceStatusType;
}
