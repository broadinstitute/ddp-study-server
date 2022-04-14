package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class ActivityInstanceStatusDto {
    @ColumnName("activity_instance_status_id")
    long id;

    @ColumnName("activity_instance_id")
    long instanceId;

    @ColumnName("operator_id")
    long operatorId;

    @ColumnName("updated_at")
    long updatedAt;

    @ColumnName("activity_instance_status_type_code")
    InstanceStatusType type;
}
