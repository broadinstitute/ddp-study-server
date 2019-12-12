package org.broadinstitute.ddp.json;

import javax.validation.constraints.NotEmpty;

import com.google.gson.annotations.SerializedName;

public class ActivityInstanceCreationPayload {

    @NotEmpty
    @SerializedName("activityCode")
    private String activityCode;

    public ActivityInstanceCreationPayload(String activityCode) {
        this.activityCode = activityCode;
    }

    public String getActivityCode() {
        return activityCode;
    }

}
