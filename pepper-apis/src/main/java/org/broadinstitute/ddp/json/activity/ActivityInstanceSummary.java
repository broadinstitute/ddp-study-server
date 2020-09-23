package org.broadinstitute.ddp.json.activity;

import java.util.Optional;

import com.google.gson.annotations.SerializedName;
import org.apache.commons.lang3.StringUtils;

public class ActivityInstanceSummary implements TranslatedSummary {

    @SerializedName("activityCode")
    private String activityCode;

    @SerializedName("instanceGuid")
    private String activityInstanceGuid;

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

    @SerializedName("isFollowup")
    private boolean isFollowup;

    private transient long activityInstanceId;
    private transient String isoLanguageCode;
    private transient String activityTypeName;
    private transient boolean excludeFromDisplay;
    private transient boolean isHidden;
    private transient String activitySecondName;
    private transient int instanceNumber;

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
            String activityTypeName,
            boolean excludeFromDisplay,
            boolean isHidden,
            long createdAt,
            boolean isFollowup
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
        this.activityTypeName = activityTypeName;
        this.excludeFromDisplay = excludeFromDisplay;
        this.isHidden = isHidden;
        this.createdAt = createdAt;
        this.isFollowup = isFollowup;
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

    public String getActivitySubtitle() {
        return activitySubtitle;
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

    public String getActivityTypeName() {
        return activityTypeName;
    }

    public long getCreatedAt() {
        return createdAt;
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

    public int getInstanceNumber() {
        return instanceNumber;
    }

    public void setInstanceNumber(int instanceNumber) {
        this.instanceNumber = instanceNumber;
    }
}
