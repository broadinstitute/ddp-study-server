package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;

public class ActivityInstanceCreationResponse {

    @SerializedName("instanceGuid")
    private String instanceGuid;

    public ActivityInstanceCreationResponse(String instanceGuid) {
        this.instanceGuid = instanceGuid;
    }

    public String getInstanceGuid() {
        return instanceGuid;
    }
}
