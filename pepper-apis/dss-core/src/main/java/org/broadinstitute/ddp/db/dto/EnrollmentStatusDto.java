package org.broadinstitute.ddp.db.dto;

import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.jdbi.v3.core.mapper.reflect.ColumnName;

public class EnrollmentStatusDto {
    private long userStudyEnrollmentId;
    private long userId;
    private String userGuid;
    private long studyId;
    private String studyGuid;
    private EnrollmentStatusType enrollmentStatus;
    private long validFromMillis;
    private Long validToMillis;

    public EnrollmentStatusDto(
            @ColumnName("user_study_enrollment_id") long userStudyEnrollmentId,
            @ColumnName("user_id") long userId,
            @ColumnName("user_guid") String userGuid,
            @ColumnName("study_id") long studyId,
            @ColumnName("study_guid") String studyGuid,
            @ColumnName("enrollment_status") EnrollmentStatusType enrollmentStatus,
            @ColumnName("valid_from_millis") long validFromMillis,
            @ColumnName("valid_to_millis") Long validToMillis) {
        this.userStudyEnrollmentId = userStudyEnrollmentId;
        this.userId = userId;
        this.studyId = studyId;
        this.enrollmentStatus = enrollmentStatus;
        this.validFromMillis = validFromMillis;
        this.userGuid = userGuid;
        this.studyGuid = studyGuid;
        this.validToMillis = validToMillis;
    }

    public long getUserId() {
        return userId;
    }

    public long getStudyId() {
        return studyId;
    }

    public EnrollmentStatusType getEnrollmentStatus() {
        return enrollmentStatus;
    }

    public long getValidFromMillis() {
        return validFromMillis;
    }

    public Long getValidToMillis() {
        return validToMillis;
    }

    public long getUserStudyEnrollmentId() {
        return userStudyEnrollmentId;
    }

    public String getUserGuid() {
        return userGuid;
    }

    public String getStudyGuid() {
        return studyGuid;
    }
}
