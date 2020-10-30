package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.user.UserProfile;

public class GovernedParticipant {

    @SerializedName("userGuid")
    private String userGuid;
    @SerializedName("alias")
    private String alias;
    @SerializedName("userProfile")
    private Profile userProfile;

    public GovernedParticipant(String userGuid, String alias, UserProfile userProfile) {
        this.userGuid = userGuid;
        this.alias = alias;
        this.userProfile = userProfile == null ? null : new Profile(userProfile);
    }

    public String getUserGuid() {
        return userGuid;
    }

    public String getAlias() {
        return alias;
    }

    public Profile getUserProfile() {
        return userProfile;
    }
}
