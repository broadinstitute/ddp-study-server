package org.broadinstitute.ddp.db.dto;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Data
@RequiredArgsConstructor(onConstructor = @__(@JdbiConstructor))
public final class ActivityInstanceSummaryDto {
    @ColumnName("activity_instance_id")
    private final long id;

    @ColumnName("activity_instance_guid")
    private final String guid;

    @ColumnName("study_id")
    private final long studyId;

    @ColumnName("study_activity_id")
    private final long activityId;

    @ColumnName("study_activity_code")
    private final String activityCode;

    @ColumnName("parent_instance_id")
    private final Long parentInstanceId;

    @ColumnName("parent_instance_guid")
    private final String parentInstanceGuid;

    @ColumnName("parent_activity_id")
    private final Long parentActivityId;

    @ColumnName("parent_activity_code")
    private final String parentActivityCode;

    @ColumnName("participant_id")
    private final long participantId;

    @ColumnName("created_at")
    private final long createdAtMillis;

    @ColumnName("is_readonly")
    private final Boolean isReadonly;

    @ColumnName("is_hidden")
    private final boolean hidden;

    @ColumnName("activity_instance_status_type")
    private final InstanceStatusType statusType;

    private int instanceNumber;
    private String previousInstanceGuid;
}
