package org.broadinstitute.ddp.model.activity.instance;

import java.util.Optional;

import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;

import org.broadinstitute.ddp.model.activity.types.ActivityType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.util.MiscUtil;

public class ActivityInstance {

    @NotNull
    @SerializedName("activityType")
    private ActivityType activityType;

    @SerializedName("guid")
    private String guid;

    @SerializedName("activityCode")
    private String activityCode;

    @SerializedName("statusCode")
    private InstanceStatusType statusType;

    @SerializedName("readonly")
    private Boolean readonly;

    @SerializedName("title")
    protected String title;

    @SerializedName("subtitle")
    protected String subtitle;

    @SerializedName("isFollowup")
    private boolean isFollowup;

    private transient long participantUserId;
    private transient long instanceId;
    private transient long activityId;
    private transient long createdAtMillis;
    private transient Long firstCompletedAt;

    public ActivityInstance(
            long participantUserId,
            long instanceId, long activityId, ActivityType activityType, String guid, String title, String subtitle,
            String statusTypeCode, Boolean readonly, String activityCode, long createdAtMillis, Long firstCompletedAt,
            boolean isFollowup
    ) {
        this.participantUserId = participantUserId;
        this.instanceId = instanceId;
        this.activityId = activityId;
        this.activityType = MiscUtil.checkNonNull(activityType, "activityType");
        this.guid = guid;
        this.title = title;
        this.subtitle = subtitle;
        this.statusType = InstanceStatusType.valueOf(statusTypeCode);
        this.readonly = readonly;
        this.activityCode = activityCode;
        this.createdAtMillis = createdAtMillis;
        this.firstCompletedAt = firstCompletedAt;
        this.isFollowup = isFollowup;
    }

    public long getParticipantUserId() {
        return participantUserId;
    }

    public ActivityType getActivityType() {
        return activityType;
    }

    public String getGuid() {
        return guid;
    }

    public InstanceStatusType getStatusType() {
        return statusType;
    }

    public boolean isReadonly() {
        return Optional.ofNullable(readonly).orElse(false);
    }

    public Boolean getReadonly() {
        return readonly;
    }

    public void makeReadonly() {
        this.readonly = true;
    }

    public long getInstanceId() {
        return instanceId;
    }

    public long getActivityId() {
        return activityId;
    }

    public String getActivityCode() {
        return activityCode;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public Long getFirstCompletedAt() {
        return firstCompletedAt;
    }
}
