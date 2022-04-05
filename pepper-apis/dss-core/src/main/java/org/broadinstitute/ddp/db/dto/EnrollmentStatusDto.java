package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class EnrollmentStatusDto {
    @ColumnName("user_study_enrollment_id")
    long userStudyEnrollmentId;

    @ColumnName("user_id")
    long userId;

    @ColumnName("user_guid")
    String userGuid;

    @ColumnName("study_id")
    long studyId;
    
    @ColumnName("study_guid")
    String studyGuid;

    @ColumnName("enrollment_status")
    EnrollmentStatusType enrollmentStatus;

    @ColumnName("valid_from_millis")
    long validFromMillis;

    @ColumnName("valid_to_millis")
    Long validToMillis;
}
