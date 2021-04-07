package org.broadinstitute.ddp.model.activity.instance;

import java.util.Optional;

import org.broadinstitute.ddp.db.dto.ActivityInstanceStatusDto;
import org.broadinstitute.ddp.model.activity.definition.ActivityDef;
import org.broadinstitute.ddp.model.activity.types.ActivityType;

/**
 * Represents activity instance with only metadata and user data (aka responses), without indication of activity structure or layout.
 */
public abstract class ActivityResponse {

    protected ActivityType type;
    protected long id;
    protected String guid;
    protected long participantId;
    protected Boolean isReadonly;
    protected long createdAt;
    protected Long firstCompletedAt;
    protected Long parentInstanceId;
    protected String parentInstanceGuid;
    protected long activityId;
    protected String activityCode;
    protected String activityVersionTag;
    protected Boolean isHidden;
    protected Integer sectionIndex;

    // Most of the time we just need data about the latest status.
    protected ActivityInstanceStatusDto latestStatus;

    ActivityResponse(ActivityType type,
                     long id, String guid, long participantId, Boolean isReadonly, long createdAt, Long firstCompletedAt,
                     Long parentInstanceId, String parentInstanceGuid,
                     long activityId, String activityCode, String activityVersionTag, Boolean isHidden, Integer sectionIndex,
                     ActivityInstanceStatusDto latestStatus) {
        this.type = type;
        this.id = id;
        this.guid = guid;
        this.participantId = participantId;
        this.isReadonly = isReadonly;
        this.createdAt = createdAt;
        this.firstCompletedAt = firstCompletedAt;
        this.parentInstanceId = parentInstanceId;
        this.parentInstanceGuid = parentInstanceGuid;
        this.activityId = activityId;
        this.activityCode = activityCode;
        this.activityVersionTag = activityVersionTag;
        this.latestStatus = latestStatus;
        this.isHidden = isHidden;
        this.sectionIndex = sectionIndex;
    }

    public ActivityType getType() {
        return type;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getGuid() {
        return guid;
    }


    public long getParticipantId() {
        return participantId;
    }

    public boolean isReadonly() {
        return Optional.ofNullable(isReadonly).orElse(false);
    }

    public Boolean getReadonly() {
        return isReadonly;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public Long getFirstCompletedAt() {
        return firstCompletedAt;
    }

    public Long getParentInstanceId() {
        return parentInstanceId;
    }

    public String getParentInstanceGuid() {
        return parentInstanceGuid;
    }

    public long getActivityId() {
        return activityId;
    }

    public String getActivityCode() {
        return activityCode;
    }

    public String getActivityVersionTag() {
        return activityVersionTag;
    }

    public ActivityInstanceStatusDto getLatestStatus() {
        return latestStatus;
    }

    public String getActivityTag() {
        return ActivityDef.getTag(activityCode, activityVersionTag);
    }

    public Boolean getHidden() {
        return isHidden;
    }

    public Integer getSectionIndex() {
        return sectionIndex;
    }
}
