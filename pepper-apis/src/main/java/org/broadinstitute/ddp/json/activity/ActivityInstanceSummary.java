package org.broadinstitute.ddp.json.activity;

import com.google.gson.annotations.SerializedName;
import org.apache.commons.lang3.StringUtils;

public class ActivityInstanceSummary implements TranslatedSummary {

    @SerializedName("activityCode")
    private String activityCode;

    @SerializedName("instanceGuid")
    private String activityInstanceGuid;

    @SerializedName("activityName")
    private String activityName;

    @SerializedName("activityDashboardName")
    private String activityDashboardName;

    @SerializedName("activitySummary")
    private String activitySummary;

    @SerializedName("activityDescription")
    private String activityDescription;

    @SerializedName("activitySubtitle")
    private String activitySubtitle;

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

    @SerializedName("isFollowup")
    private boolean isFollowup;

    private transient String isoLanguageCode;
    private transient String activityTypeName;
    private transient boolean excludeFromDisplay;
    private transient boolean isHidden;

    /**
     * Instantiates ActivityInstanceSummary object.
     */
    public ActivityInstanceSummary(
            String activityCode,
            String activityInstanceGuid,
            String activityName,
            String activityDashboardName,
            String activitySummary,
            String activityDescription,
            String activitySubtitle,
            String activityType,
            String formTypeCode,
            String statusTypeCode,
            String iconBase64,
            Boolean readonly,
            String isoLanguageCode,
            String activityTypeName,
            boolean excludeFromDisplay,
            boolean isHidden,
            long createdAt,
            boolean isFollowup
    ) {
        this.activityCode = activityCode;
        this.activityInstanceGuid = activityInstanceGuid;
        this.activityName = activityName;
        this.activityDashboardName = activityDashboardName;
        this.activitySummary = activitySummary;
        this.activityDescription = activityDescription;
        this.activitySubtitle = activitySubtitle;
        this.activityType = activityType;
        if (StringUtils.isNotBlank(formTypeCode)) {
            this.activitySubType = formTypeCode;
        }
        this.statusTypeCode = statusTypeCode;
        this.iconBase64 = iconBase64;
        this.readonly = readonly;
        this.isoLanguageCode = isoLanguageCode;
        this.activityTypeName = activityTypeName;
        this.excludeFromDisplay = excludeFromDisplay;
        this.isHidden = isHidden;
        this.createdAt = createdAt;
        this.isFollowup = isFollowup;
    }

    public String getActivityCode() {
        return activityCode;
    }

    @Override
    public String getActivityInstanceGuid() {
        return activityInstanceGuid;
    }

    public String getActivityName() {
        return activityName;
    }

    public String getActivityDashboardName() {
        return activityDashboardName;
    }

    public String getActivitySummary() {
        return activitySummary;
    }

    public String getActivityDescription() {
        return activityDescription;
    }

    public String getActivitySubtitle() {
        return activitySubtitle;
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

    public Boolean isReadonly() {
        return readonly;
    }

    @Override
    public String getIsoLanguageCode() {
        return isoLanguageCode;
    }

    public String getActivityTypeName() {
        return activityTypeName;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setActivityDashboardName(String activityDashboardName) {
        this.activityDashboardName = activityDashboardName;
    }

    public boolean isExcludeFromDisplay() {
        return excludeFromDisplay;
    }

    public boolean isHidden() {
        return isHidden;
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
}
