package org.broadinstitute.ddp.db.dto;

import java.util.Objects;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class ActivityDto {

    private final long activityId;
    private final long activityTypeId;
    private final long studyId;
    private final int displayOrder;
    private final boolean writeOnce;
    private final boolean instantiateUponRegistration;
    private final Integer maxInstancesPerUser;
    private final Long editTimeoutSec;
    private final boolean allowOndemandTrigger;
    private final boolean excludeFromDisplay;
    private final boolean allowUnauthenticated;
    private final boolean isFollowup;
    private final boolean excludeStatusIconFromDisplay;
    private final boolean hideExistingInstancesOnCreation;
    private String activityCode;

    @JdbiConstructor
    public ActivityDto(
            @ColumnName("study_activity_id") long activityId,
            @ColumnName("activity_type_id") long activityTypeId,
            @ColumnName("study_id") long studyId,
            @ColumnName("study_activity_code") String activityCode,
            @ColumnName("display_order") int displayOrder,
            @ColumnName("is_write_once") boolean writeOnce,
            @ColumnName("instantiate_upon_registration") boolean instantiateUponRegistration,
            @ColumnName("max_instances_per_user") Integer maxInstancesPerUser,
            @ColumnName("edit_timeout_sec") Long editTimeoutSec,
            @ColumnName("allow_ondemand_trigger") boolean allowOndemandTrigger,
            @ColumnName("exclude_from_display") boolean excludeFromDisplay,
            @ColumnName("allow_unauthenticated") boolean allowUnauthenticated,
            @ColumnName("is_followup") boolean isFollowup,
            @ColumnName("exclude_status_icon_from_display") boolean excludeStatusIconFromDisplay,
            @ColumnName("hide_existing_instances_on_creation") boolean hideExistingInstancesOnCreation
    ) {
        this.activityId = activityId;
        this.activityTypeId = activityTypeId;
        this.studyId = studyId;
        this.activityCode = activityCode;
        this.displayOrder = displayOrder;
        this.writeOnce = writeOnce;
        this.instantiateUponRegistration = instantiateUponRegistration;
        this.maxInstancesPerUser = maxInstancesPerUser;
        this.editTimeoutSec = editTimeoutSec;
        this.allowOndemandTrigger = allowOndemandTrigger;
        this.excludeFromDisplay = excludeFromDisplay;
        this.allowUnauthenticated = allowUnauthenticated;
        this.isFollowup = isFollowup;
        this.excludeStatusIconFromDisplay = excludeStatusIconFromDisplay;
        this.hideExistingInstancesOnCreation = hideExistingInstancesOnCreation;
    }

    public long getActivityId() {
        return activityId;
    }

    public long getActivityTypeId() {
        return activityTypeId;
    }

    public long getStudyId() {
        return studyId;
    }

    public String getActivityCode() {
        return activityCode;
    }

    public void setActivityCode(String activityCode) {
        this.activityCode = activityCode;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public boolean isWriteOnce() {
        return writeOnce;
    }

    public boolean isInstantiateUponRegistration() {
        return instantiateUponRegistration;
    }

    public Integer getMaxInstancesPerUser() {
        return maxInstancesPerUser;
    }

    public Long getEditTimeoutSec() {
        return editTimeoutSec;
    }

    public boolean isOndemandTriggerAllowed() {
        return allowOndemandTrigger;
    }

    public boolean shouldExcludeFromDisplay() {
        return excludeFromDisplay;
    }

    public boolean isUnauthenticatedAllowed() {
        return allowUnauthenticated;
    }

    public boolean isFollowup() {
        return isFollowup;
    }

    public boolean shouldExcludeStatusIconFromDisplay() {
        return excludeStatusIconFromDisplay;
    }

    public boolean isHideExistingInstancesOnCreation() {
        return hideExistingInstancesOnCreation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ActivityDto that = (ActivityDto) o;
        return activityId == that.activityId
                && activityTypeId == that.activityTypeId
                && studyId == that.studyId
                && Objects.equals(activityCode, that.activityCode)
                && displayOrder == that.displayOrder
                && writeOnce == that.writeOnce
                && instantiateUponRegistration == that.instantiateUponRegistration
                && Objects.equals(maxInstancesPerUser, that.maxInstancesPerUser)
                && Objects.equals(editTimeoutSec, that.editTimeoutSec)
                && allowOndemandTrigger == that.allowOndemandTrigger
                && excludeFromDisplay == that.excludeFromDisplay
                && allowUnauthenticated == that.allowUnauthenticated
                && isFollowup == that.isFollowup
                && excludeStatusIconFromDisplay == that.excludeStatusIconFromDisplay
                && hideExistingInstancesOnCreation == that.hideExistingInstancesOnCreation;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                activityId,
                activityTypeId,
                studyId,
                activityCode,
                displayOrder,
                writeOnce,
                instantiateUponRegistration,
                maxInstancesPerUser,
                editTimeoutSec,
                allowOndemandTrigger,
                excludeFromDisplay,
                allowUnauthenticated,
                isFollowup,
                excludeStatusIconFromDisplay,
                hideExistingInstancesOnCreation);
    }
}
