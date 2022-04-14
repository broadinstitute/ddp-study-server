package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.Accessors;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class ActivityDto {
    @ColumnName("study_activity_id")
    long activityId;

    @ColumnName("activity_type_id")
    long activityTypeId;

    @ColumnName("study_id")
    long studyId;

    @ColumnName("study_activity_code")
    String activityCode;

    @ColumnName("parent_activity_id")
    Long parentActivityId;

    @ColumnName("parent_activity_code")
    String parentActivityCode;

    @ColumnName("display_order")
    int displayOrder;

    @Accessors(fluent = true)
    @ColumnName("is_write_once")
    boolean writeOnce;

    @Accessors(fluent = true)
    @ColumnName("instantiate_upon_registration")
    boolean instantiateUponRegistration;

    @ColumnName("max_instances_per_user")
    Integer maxInstancesPerUser;

    @ColumnName("edit_timeout_sec")
    Long editTimeoutSec;

    @ColumnName("allow_ondemand_trigger")
    boolean onDemandTriggerAllowed;

    @Accessors(fluent = true)
    @ColumnName("exclude_from_display")
    boolean shouldExcludeFromDisplay;

    @ColumnName("allow_unauthenticated")
    boolean unauthenticatedAllowed;

    @ColumnName("is_followup")
    boolean isFollowup;

    @Accessors(fluent = true)
    @ColumnName("exclude_status_icon_from_display")
    boolean shouldExcludeStatusIconFromDisplay;

    @Accessors(fluent = true)
    @ColumnName("hide_existing_instances_on_creation")
    boolean hideExistingInstancesOnCreation;

    @ColumnName("create_on_parent_creation")
    boolean createOnParentCreation;

    @Accessors(fluent = true)
    @ColumnName("can_delete_instances")
    boolean canDeleteInstances;

    @Accessors(fluent = true)
    @ColumnName("can_delete_first_instance")
    Boolean canDeleteFirstInstance;

    @Accessors(fluent = true)
    @ColumnName("show_activity_status")
    boolean showActivityStatus;
}
