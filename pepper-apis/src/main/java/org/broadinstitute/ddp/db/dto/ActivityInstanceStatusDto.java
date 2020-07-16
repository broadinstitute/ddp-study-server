package org.broadinstitute.ddp.db.dto;

import static org.broadinstitute.ddp.constants.SqlConstants.ActivityInstanceStatusTable.ID;
import static org.broadinstitute.ddp.constants.SqlConstants.ActivityInstanceStatusTable.INSTANCE_ID;
import static org.broadinstitute.ddp.constants.SqlConstants.ActivityInstanceStatusTable.OPERATOR_ID;
import static org.broadinstitute.ddp.constants.SqlConstants.ActivityInstanceStatusTable.TYPE_ID;
import static org.broadinstitute.ddp.constants.SqlConstants.ActivityInstanceStatusTable.UPDATED_AT;
import static org.broadinstitute.ddp.constants.SqlConstants.ActivityInstanceStatusTypeTable.ACTIVITY_STATUS_TYPE_CODE;

import java.beans.ConstructorProperties;

import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;

public class ActivityInstanceStatusDto {

    private long id;
    private long typeId;
    private long instanceId;
    private long operatorId;
    private long updatedAt;
    private InstanceStatusType type;

    @ConstructorProperties({ID, TYPE_ID, INSTANCE_ID, OPERATOR_ID, UPDATED_AT, ACTIVITY_STATUS_TYPE_CODE})
    public ActivityInstanceStatusDto(long id, long typeId, long instanceId, long operatorId, long updatedAt, InstanceStatusType type) {
        this.id = id;
        this.typeId = typeId;
        this.instanceId = instanceId;
        this.operatorId = operatorId;
        this.updatedAt = updatedAt;
        this.type = type;
    }

    public long getId() {
        return id;
    }

    public long getInstanceId() {
        return instanceId;
    }

    public long getOperatorId() {
        return operatorId;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public InstanceStatusType getType() {
        return type;
    }
}
