package org.broadinstitute.ddp.db.dto;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class ActivityInstanceDto {

    private long id;
    private String guid;
    private long activityId;
    private long participantId;
    private long createdAtMillis;
    private boolean isReadonly;
    private boolean isHidden;
    private String statusTypeCode;
    private Long onDemandTriggerId;
    private long submissionId;
    private String sessionId;
    private Long firstCompletedAt;
    private boolean allowUnauthenticated;

    @JdbiConstructor
    public ActivityInstanceDto(
            @ColumnName("activity_instance_id") long id,
            @ColumnName("activity_instance_guid") String guid,
            @ColumnName("study_activity_id") long activityId,
            @ColumnName("participant_id") long participantId,
            @ColumnName("created_at") long createdAtMillis,
            @ColumnName("first_completed_at") Long firstCompletedAt,
            @ColumnName("is_readonly") boolean isReadonly,
            @ColumnName("is_hidden") boolean isHidden,
            @ColumnName("activity_instance_status_type_code") String statusTypeCode,
            @ColumnName("ondemand_trigger_id") Long onDemandTriggerId,
            @ColumnName("allow_unauthenticated") boolean allowUnauthenticated
    ) {
        this.id = id;
        this.guid = guid;
        this.activityId = activityId;
        this.participantId = participantId;
        this.createdAtMillis = createdAtMillis;
        this.firstCompletedAt = firstCompletedAt;
        this.isReadonly = isReadonly;
        this.isHidden = isHidden;
        this.statusTypeCode = statusTypeCode;
        this.onDemandTriggerId = onDemandTriggerId;
        this.allowUnauthenticated = allowUnauthenticated;
    }

    /**
     * Instantiate ActivityInstanceDto object.
     */
    public ActivityInstanceDto(long id,
                               String guid,
                               long activityId,
                               long participantId,
                               long createdAtMillis,
                               Long firstCompletedAt,
                               boolean isReadonly,
                               String statusTypeCode,
                               long submissionId,
                               String sessionId) {
        this.id = id;
        this.guid = guid;
        this.activityId = activityId;
        this.participantId = participantId;
        this.createdAtMillis = createdAtMillis;
        this.firstCompletedAt = firstCompletedAt;
        this.isReadonly = isReadonly;
        this.isHidden = false;
        this.statusTypeCode = statusTypeCode;
        this.submissionId = submissionId;
        this.sessionId = sessionId;
    }

    public long getId() {
        return id;
    }

    public String getGuid() {
        return guid;
    }

    public long getActivityId() {
        return activityId;
    }

    public long getParticipantId() {
        return participantId;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public Long getFirstCompletedAt() {
        return firstCompletedAt;
    }

    public boolean isReadonly() {
        return isReadonly;
    }

    public boolean isHidden() {
        return isHidden;
    }

    public String getStatusTypeCode() {
        return statusTypeCode;
    }

    public Long getOnDemandTriggerId() {
        return onDemandTriggerId;
    }

    public long getSubmissionId() {
        return submissionId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public boolean isAllowUnauthenticated() {
        return allowUnauthenticated;
    }
}
