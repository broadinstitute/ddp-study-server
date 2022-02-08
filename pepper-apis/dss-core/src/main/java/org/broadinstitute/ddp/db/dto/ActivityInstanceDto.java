package org.broadinstitute.ddp.db.dto;

import org.broadinstitute.ddp.model.activity.types.ActivityType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class ActivityInstanceDto {

    private long id;
    private String guid;
    private long studyId;
    private long activityId;
    private String activityCode;
    private Long parentInstanceId;
    private String parentInstanceGuid;
    private Long parentActivityId;
    private String parentActivityCode;
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
    private int sectionIndex;

    @JdbiConstructor
    public ActivityInstanceDto(
            @ColumnName("activity_instance_id") long id,
            @ColumnName("activity_instance_guid") String guid,
            @ColumnName("study_id") long studyId,
            @ColumnName("study_activity_id") long activityId,
            @ColumnName("study_activity_code") String activityCode,
            @ColumnName("parent_instance_id") Long parentInstanceId,
            @ColumnName("parent_instance_guid") String parentInstanceGuid,
            @ColumnName("parent_activity_id") Long parentActivityId,
            @ColumnName("parent_activity_code") String parentActivityCode,
            @ColumnName("participant_id") long participantId,
            @ColumnName("created_at") long createdAtMillis,
            @ColumnName("first_completed_at") Long firstCompletedAt,
            @ColumnName("is_readonly") Boolean isReadonly,
            @ColumnName("is_hidden") boolean isHidden,
            @ColumnName("activity_instance_status_type") InstanceStatusType statusType,
            @ColumnName("activity_type") ActivityType activityType,
            @ColumnName("ondemand_trigger_id") Long onDemandTriggerId,
            @ColumnName("allow_unauthenticated") boolean allowUnauthenticated,
            @ColumnName("section_index") int sectionIndex
    ) {
        this.id = id;
        this.guid = guid;
        this.studyId = studyId;
        this.activityId = activityId;
        this.activityCode = activityCode;
        this.parentInstanceId = parentInstanceId;
        this.parentInstanceGuid = parentInstanceGuid;
        this.parentActivityId = parentActivityId;
        this.parentActivityCode = parentActivityCode;
        this.participantId = participantId;
        this.createdAtMillis = createdAtMillis;
        this.firstCompletedAt = firstCompletedAt;
        this.isReadonly = isReadonly;
        this.isHidden = isHidden;
        this.statusType = statusType;
        this.activityType = activityType;
        this.onDemandTriggerId = onDemandTriggerId;
        this.allowUnauthenticated = allowUnauthenticated;
        this.sectionIndex = sectionIndex;
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

    public String getActivityCode() {
        return activityCode;
    }

    public Long getParentInstanceId() {
        return parentInstanceId;
    }

    public String getParentInstanceGuid() {
        return parentInstanceGuid;
    }

    public Long getParentActivityId() {
        return parentActivityId;
    }

    public String getParentActivityCode() {
        return parentActivityCode;
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

    public int getSectionIndex() {
        return sectionIndex;
    }
}
