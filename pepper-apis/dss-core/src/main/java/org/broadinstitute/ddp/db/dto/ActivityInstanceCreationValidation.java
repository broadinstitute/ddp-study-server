package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class ActivityInstanceCreationValidation {
    @ColumnName("study_activity_id")
    long activityId;
    
    @ColumnName("parent_activity_id")
    Long parentActivityId;
    
    @ColumnName("parent_activity_code")
    String parentActivityCode;

    @ColumnName("hide_existing_instances_on_creation")
    boolean hideExistingInstancesOnCreation;
    
    @ColumnName("max_instances_per_user")
    Integer maxInstancesPerUser;

    @ColumnName("num_instances_for_user")
    int numInstancesForUser;

    public boolean hasTooManyInstances() {
        return (maxInstancesPerUser != null) && (maxInstancesPerUser - numInstancesForUser) <= 0;
    }
}
