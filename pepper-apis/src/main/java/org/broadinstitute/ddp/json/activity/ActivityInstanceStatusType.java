package org.broadinstitute.ddp.json.activity;

import com.google.gson.annotations.SerializedName;

public class ActivityInstanceStatusType {
    @SerializedName("code")
    private String activityInstanceStatusTypeCode;
    @SerializedName("name")
    private String activityInstanceStatusTypeName;

    public ActivityInstanceStatusType(String activityInstanceStatusTypeCode, String activityInstanceStatusTypeName) {
        this.activityInstanceStatusTypeCode = activityInstanceStatusTypeCode;
        this.activityInstanceStatusTypeName = activityInstanceStatusTypeName;
    }

    public String getActivityInstanceStatusTypeCode() {
        return activityInstanceStatusTypeCode;
    }

    public String getActivityInstanceStatusTypeName() {
        return activityInstanceStatusTypeName;
    }
}
