package org.broadinstitute.ddp.json.activity;

import java.util.Optional;

import com.google.gson.annotations.SerializedName;
import org.apache.commons.lang3.StringUtils;

public class ActivityInstanceSummary implements TranslatedSummary {

    @SerializedName("activityCode")
    private String activityCode;

    @SerializedName("instanceGuid")
    private String activityInstanceGuid;

    @SerializedName("parentInstanceGuid")
    private String parentInstanceGuid;

    @SerializedName("activityName")
    private String activityName;

    @SerializedName("activityTitle")
    private String activityTitle;

    @SerializedName("activitySubtitle")
    private String activitySubtitle;

    @SerializedName("activityDescription")
    private String activityDescription;

    @SerializedName("activitySummary")
    private String activitySummary;

    @SerializedName("statusCode")
    private String statusTypeCode;

    @SerializedName("activityType")
    private String activityType;

    @SerializedName("activitySubtype")
    private String activitySubType;

    @SerializedName("icon")
    private String iconBase64;

    @SerializedName("readonly")
    private Boolean readonly;

    @SerializedName("createdAt")
    private long createdAt;

    @SerializedName("numQuestions")
    private int numQuestions;

    @SerializedName("numQuestionsAnswered")
    private int numQuestionsAnswered;

    @SerializedName("canDelete")
    private boolean canDelete;

    @SerializedName("isFollowup")
    private boolean isFollowup;

    @SerializedName("previousInstanceGuid")
    private String previousInstanceGuid;

    @SerializedName("isHidden")
    private boolean isHidden;

    private transient long activityInstanceId;
    private transient String isoLanguageCode;
    private transient boolean excludeFromDisplay;
    private transient boolean isInstanceHidden;
    private transient String activitySecondName;
    private transient int instanceNumber;
    private transient String versionTag;
    private transient long versionId;
    private transient long revisionStart;

    /**
     * Instantiates ActivityInstanceSummary object.
     */
    public ActivityInstanceSummary(
            String activityCode,
            long activityInstanceId,
            String activityInstanceGuid,
            String activityName,
            String activitySecondName,
            String activityTitle,
            String activitySubtitle,
            String activityDescription,
            String activitySummary,
            String activityType,
            String formTypeCode,
            String statusTypeCode,
            String iconBase64,
            Boolean readonly,
            String isoLanguageCode,
            boolean excludeFromDisplay,
            boolean isInstanceHidden,
            long createdAt,
            boolean canDelete,
            boolean isFollowup,
            String versionTag,
            long versionId,
            long revisionStart
    ) {
        this.activityCode = activityCode;
        this.activityInstanceId = activityInstanceId;
        this.activityInstanceGuid = activityInstanceGuid;
        this.activityName = activityName;
        this.activitySecondName = activitySecondName;
        this.activityTitle = activityTitle;
        this.activitySubtitle = activitySubtitle;
        this.activityDescription = activityDescription;
        this.activitySummary = activitySummary;
        this.activityType = activityType;
        if (StringUtils.isNotBlank(formTypeCode)) {
            this.activitySubType = formTypeCode;
        }
        this.statusTypeCode = statusTypeCode;
        this.iconBase64 = iconBase64;
        this.readonly = readonly;
        this.isoLanguageCode = isoLanguageCode;
        this.excludeFromDisplay = excludeFromDisplay;
        this.isInstanceHidden = isInstanceHidden;
        this.isHidden = isInstanceHidden || excludeFromDisplay;
        this.createdAt = createdAt;
        this.canDelete = canDelete;
        this.isFollowup = isFollowup;
        this.versionTag = versionTag;
        this.versionId = versionId;
        this.revisionStart = revisionStart;
    }

    public String getActivityCode() {
        return activityCode;
    }

    public long getActivityInstanceId() {
        return activityInstanceId;
    }

    @Override
    public String getActivityInstanceGuid() {
        return activityInstanceGuid;
    }

    public String getActivityName() {
        return activityName;
    }

    public void setActivityName(String activityName) {
        this.activityName = activityName;
    }

    public String getActivitySecondName() {
        return activitySecondName;
    }

    public String getActivityTitle() {
        return activityTitle;
    }

    public void setActivityTitle(String activityTitle) {
        this.activityTitle = activityTitle;
    }

    public String getActivitySubtitle() {
        return activitySubtitle;
    }

    public void setActivitySubtitle(String activitySubtitle) {
        this.activitySubtitle = activitySubtitle;
    }

    public String getActivityDescription() {
        return activityDescription;
    }

    public String getActivitySummary() {
        return activitySummary;
    }

    public void setActivitySummary(String activitySummary) {
        this.activitySummary = activitySummary;
    }

    public String getStatusTypeCode() {
        return statusTypeCode;
    }

    public String getActivityType() {
        return activityType;
    }

    public String getActivitySubType() {
        return activitySubType;
    }

    public String getIconBase64() {
        return iconBase64;
    }

    public boolean isReadonly() {
        return Optional.ofNullable(readonly).orElse(false);
    }

    public Boolean getReadonly() {
        return readonly;
    }

    @Override
    public String getIsoLanguageCode() {
        return isoLanguageCode;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public boolean canDelete() {
        return canDelete;
    }

    /**
     * Returns whether the activity was defined to be excluded from display.
     */
    public boolean isExcludeFromDisplay() {
        return excludeFromDisplay;
    }

    /**
     * Returns whether this specific instance is hidden.
     */
    public boolean isInstanceHidden() {
        return isInstanceHidden;
    }

    /**
     * Returns whether instance is hidden or not, either at the activity definition or instance level.
     */
    public boolean isHidden() {
        return isHidden;
    }

    public boolean isFollowup() {
        return isFollowup;
    }

    public String getVersionTag() {
        return versionTag;
    }

    public long getVersionId() {
        return versionId;
    }

    public long getRevisionStart() {
        return revisionStart;
    }

    public int getNumQuestions() {
        return numQuestions;
    }

    public void setNumQuestions(int numQuestions) {
        this.numQuestions = numQuestions;
    }

    public int getNumQuestionsAnswered() {
        return numQuestionsAnswered;
    }

    public void setNumQuestionsAnswered(int numQuestionsAnswered) {
        this.numQuestionsAnswered = numQuestionsAnswered;
    }

    public int getInstanceNumber() {
        return instanceNumber;
    }

    public void setInstanceNumber(int instanceNumber) {
        this.instanceNumber = instanceNumber;
    }

    public void setPreviousInstanceGuid(String previousInstanceGuid) {
        this.previousInstanceGuid = previousInstanceGuid;
    }

    public String getParentInstanceGuid() {
        return parentInstanceGuid;
    }

    public void setParentInstanceGuid(String parentInstanceGuid) {
        this.parentInstanceGuid = parentInstanceGuid;
    }
}
