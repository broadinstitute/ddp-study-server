package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;

public class UserRegistrationResponse {

    @SerializedName("ddpUserGuid")
    private String ddpUserGuid;

    public UserRegistrationResponse(String ddpUserGuid) {
        this.ddpUserGuid = ddpUserGuid;
    }

    public String getDdpUserGuid() {
        return ddpUserGuid;
    }
}
