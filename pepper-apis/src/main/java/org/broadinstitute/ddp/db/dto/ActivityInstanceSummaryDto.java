package org.broadinstitute.ddp.db.dto;

import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class ActivityInstanceSummaryDto {

    private long id;
    private String guid;
    private long studyId;
    private long activityId;
    private String activityCode;
    private Long parentActivityId;
    private String parentActivityCode;
    private long participantId;
    private long createdAtMillis;
    private Boolean isReadonly;
    private boolean isHidden;
    private InstanceStatusType statusType;

    @JdbiConstructor
    public ActivityInstanceSummaryDto(
            @ColumnName("activity_instance_id") long id,
            @ColumnName("activity_instance_guid") String guid,
            @ColumnName("study_id") long studyId,
            @ColumnName("study_activity_id") long activityId,
            @ColumnName("study_activity_code") String activityCode,
            @ColumnName("parent_activity_id") Long parentActivityId,
            @ColumnName("parent_activity_code") String parentActivityCode,
            @ColumnName("participant_id") long participantId,
            @ColumnName("created_at") long createdAtMillis,
            @ColumnName("is_readonly") Boolean isReadonly,
            @ColumnName("is_hidden") boolean isHidden,
            @ColumnName("activity_instance_status_type") InstanceStatusType statusType) {
        this.id = id;
        this.guid = guid;
        this.studyId = studyId;
        this.activityId = activityId;
        this.activityCode = activityCode;
        this.parentActivityId = parentActivityId;
        this.parentActivityCode = parentActivityCode;
        this.participantId = participantId;
        this.createdAtMillis = createdAtMillis;
        this.isReadonly = isReadonly;
        this.isHidden = isHidden;
        this.statusType = statusType;
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

    public Boolean getReadonly() {
        return isReadonly;
    }

    public boolean isHidden() {
        return isHidden;
    }

    public InstanceStatusType getStatusType() {
        return statusType;
    }
}
