package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.SuperBuilder;
import org.broadinstitute.ddp.audit.AuditActionType;
import org.broadinstitute.ddp.audit.AuditEntityType;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
@SuperBuilder(toBuilder = true)
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class AuditTrailDto {
    @ColumnName("audit_trail_id")
    Long auditTrailId;

    @ColumnName("study_id")
    Long studyId;

    @ColumnName("operator_id")
    Long operatorId;

    @ColumnName("subject_user_id")
    Long subjectUserId;

    @ColumnName("activity_instance_id")
    Long activityInstanceId;

    @ColumnName("answer_id")
    Long answerId;

    @ColumnName("entity_type")
    AuditEntityType entityType;

    @ColumnName("action_type")
    AuditActionType actionType;

    @ColumnName("operator_guid")
    String operatorGuid;

    @ColumnName("subject_user_guid")
    String subjectUserGuid;

    @ColumnName("description")
    String description;

    @ColumnName("time")
    java.sql.Timestamp time;
}
