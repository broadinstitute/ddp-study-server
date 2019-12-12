package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;

public class GovernedParticipant {

    @SerializedName("userGuid")
    private String userGuid;
    @SerializedName("alias")
    private String alias;

    public GovernedParticipant(String userGuid, String alias) {
        this.userGuid = userGuid;
        this.alias = alias;
    }

    public String getUserGuid() {
        return userGuid;
    }

    public String getAlias() {
        return alias;
    }
}
