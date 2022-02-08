package org.broadinstitute.ddp.json.activity;

import java.io.Serializable;

import com.google.gson.annotations.SerializedName;

public class ActivityInstanceStatusType implements Serializable {
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
