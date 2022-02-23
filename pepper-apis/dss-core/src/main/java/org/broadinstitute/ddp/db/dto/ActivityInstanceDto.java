package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.broadinstitute.ddp.model.activity.types.ActivityType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class ActivityInstanceDto {
    @ColumnName("activity_instance_id")
    long id;

    @ColumnName("activity_instance_guid")
    String guid;

    @ColumnName("study_id")
    long studyId;

    @ColumnName("study_activity_id")
    long activityId;

    @ColumnName("study_activity_code")
    String activityCode;

    @ColumnName("parent_instance_id")
    Long parentInstanceId;

    @ColumnName("parent_instance_guid")
    String parentInstanceGuid;

    @ColumnName("parent_activity_id")
    Long parentActivityId;

    @ColumnName("parent_activity_code")
    String parentActivityCode;

    @ColumnName("participant_id")
    long participantId;

    @ColumnName("created_at")
    long createdAtMillis;

    @ColumnName("first_completed_at")
    Long firstCompletedAt;

    @ColumnName("is_readonly")
    Boolean isReadonly;

    @ColumnName("is_hidden")
    boolean isHidden;

    @ColumnName("activity_instance_status_type")
    InstanceStatusType statusType;

    @ColumnName("activity_type")
    ActivityType activityType;

    @ColumnName("ondemand_trigger_id")
    Long onDemandTriggerId;

    @ColumnName("allow_unauthenticated")
    boolean allowUnauthenticated;

    @ColumnName("section_index")
    int sectionIndex;
}
