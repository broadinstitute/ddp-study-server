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

    protected long activityId;
    protected String activityCode;
    protected String activityVersionTag;

    // Most of the time we just need data about the latest status.
    protected ActivityInstanceStatusDto latestStatus;

    ActivityResponse(ActivityType type,
                     long id, String guid, long participantId, Boolean isReadonly, long createdAt, Long firstCompletedAt,
                     long activityId, String activityCode, String activityVersionTag,
                     ActivityInstanceStatusDto latestStatus) {
        this.type = type;
        this.id = id;
        this.guid = guid;
        this.participantId = participantId;
        this.isReadonly = isReadonly;
        this.createdAt = createdAt;
        this.firstCompletedAt = firstCompletedAt;
        this.activityId = activityId;
        this.activityCode = activityCode;
        this.activityVersionTag = activityVersionTag;
        this.latestStatus = latestStatus;
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
}
