package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;

public class User {

    public User(String ddpUserId) {
        this.ddpUserId = ddpUserId;
    }


    @SerializedName("ddpUserId")
    private String ddpUserId;

    /**
     * Maps to user.guid
     */
    public String getDdpUserId() {
        return ddpUserId;
    }
}
