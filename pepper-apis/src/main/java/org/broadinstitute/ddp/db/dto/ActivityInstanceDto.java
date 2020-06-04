package org.broadinstitute.ddp.db.dto;

import java.util.Optional;

import org.broadinstitute.ddp.model.activity.types.ActivityType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class ActivityInstanceDto {

    private long id;
    private String guid;
    private long studyId;
    private long activityId;
    private long participantId;
    private long createdAtMillis;
    private Boolean isReadonly;
    private boolean isHidden;
    private InstanceStatusType statusType;
    private ActivityType activityType;
    private Long onDemandTriggerId;
    private long submissionId;
    private String sessionId;
    private Long firstCompletedAt;
    private boolean allowUnauthenticated;
    private int lastVisitedActivitySection;

    @JdbiConstructor
    public ActivityInstanceDto(
            @ColumnName("activity_instance_id") long id,
            @ColumnName("activity_instance_guid") String guid,
            @ColumnName("study_id") long studyId,
            @ColumnName("study_activity_id") long activityId,
            @ColumnName("participant_id") long participantId,
            @ColumnName("created_at") long createdAtMillis,
            @ColumnName("first_completed_at") Long firstCompletedAt,
            @ColumnName("is_readonly") Boolean isReadonly,
            @ColumnName("is_hidden") boolean isHidden,
            @ColumnName("activity_instance_status_type") InstanceStatusType statusType,
            @ColumnName("activity_type") ActivityType activityType,
            @ColumnName("ondemand_trigger_id") Long onDemandTriggerId,
            @ColumnName("allow_unauthenticated") boolean allowUnauthenticated,
            @ColumnName("last_visited_section") int lastVisitedActivitySection
    ) {
        this.id = id;
        this.guid = guid;
        this.studyId = studyId;
        this.activityId = activityId;
        this.participantId = participantId;
        this.createdAtMillis = createdAtMillis;
        this.firstCompletedAt = firstCompletedAt;
        this.isReadonly = isReadonly;
        this.isHidden = isHidden;
        this.statusType = statusType;
        this.activityType = activityType;
        this.onDemandTriggerId = onDemandTriggerId;
        this.allowUnauthenticated = allowUnauthenticated;
        this.lastVisitedActivitySection = lastVisitedActivitySection;
    }

    public long getId() {
        return id;
    }

    public String getGuid() {
        return guid;
    }

    public long getStudyId() {
        return studyId;
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
        return Optional.ofNullable(isReadonly).orElse(false);
    }

    public Boolean getReadonly() {
        return isReadonly;
    }

    public boolean isHidden() {
        return isHidden;
    }

    public InstanceStatusType getStatusType() {
        return statusType;
    }

    public ActivityType getActivityType() {
        return activityType;
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

    public int getLastVisitedActivitySection() {
        return lastVisitedActivitySection;
    }
}
