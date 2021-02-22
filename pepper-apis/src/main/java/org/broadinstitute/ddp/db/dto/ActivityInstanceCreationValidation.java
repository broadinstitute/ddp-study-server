package org.broadinstitute.ddp.db.dto;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class ActivityInstanceCreationValidation {

    private final long activityId;
    private final Long parentActivityId;
    private final String parentActivityCode;
    private final boolean hideExistingInstancesOnCreation;
    private final Integer maxInstancesPerUser;
    private final int numInstancesForUser;
    private final boolean hasTooManyInstances;

    @JdbiConstructor
    public ActivityInstanceCreationValidation(
            @ColumnName("study_activity_id") long activityId,
            @ColumnName("parent_activity_id") Long parentActivityId,
            @ColumnName("parent_activity_code") String parentActivityCode,
            @ColumnName("hide_existing_instances_on_creation") boolean hideExistingInstancesOnCreation,
            @ColumnName("max_instances_per_user") Integer maxInstancesPerUser,
            @ColumnName("num_instances_for_user") int numInstancesForUser) {
        this.activityId = activityId;
        this.parentActivityId = parentActivityId;
        this.parentActivityCode = parentActivityCode;
        this.hideExistingInstancesOnCreation = hideExistingInstancesOnCreation;
        this.maxInstancesPerUser = maxInstancesPerUser;
        this.numInstancesForUser = numInstancesForUser;
        if (maxInstancesPerUser != null) {
            this.hasTooManyInstances = (maxInstancesPerUser - numInstancesForUser) <= 0;
        } else {
            this.hasTooManyInstances = false;
        }
    }

    public long getActivityId() {
        return activityId;
    }

    public Long getParentActivityId() {
        return parentActivityId;
    }

    public String getParentActivityCode() {
        return parentActivityCode;
    }

    public boolean isHideExistingInstancesOnCreation() {
        return hideExistingInstancesOnCreation;
    }

    public Integer getMaxInstancesPerUser() {
        return maxInstancesPerUser;
    }

    public int getNumInstancesForUser() {
        return numInstancesForUser;
    }

    public boolean hasTooManyInstances() {
        return hasTooManyInstances;
    }
}
