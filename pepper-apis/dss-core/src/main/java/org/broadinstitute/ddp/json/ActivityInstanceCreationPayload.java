package org.broadinstitute.ddp.json;

import javax.validation.constraints.NotEmpty;

import com.google.gson.annotations.SerializedName;

public class ActivityInstanceCreationPayload {

    @NotEmpty
    @SerializedName("activityCode")
    private String activityCode;

    @SerializedName("parentInstanceGuid")
    private String parentInstanceGuid;

    public ActivityInstanceCreationPayload(String activityCode) {
        this.activityCode = activityCode;
    }

    public ActivityInstanceCreationPayload(String activityCode, String parentInstanceGuid) {
        this.activityCode = activityCode;
        this.parentInstanceGuid = parentInstanceGuid;
    }

    public String getActivityCode() {
        return activityCode;
    }

    public String getParentInstanceGuid() {
        return parentInstanceGuid;
    }
}
