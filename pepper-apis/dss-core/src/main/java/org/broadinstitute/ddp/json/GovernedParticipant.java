package org.broadinstitute.ddp.json;

import java.util.Optional;

import com.google.gson.annotations.SerializedName;
import lombok.Value;
import org.broadinstitute.ddp.model.user.UserProfile;

@Value
public class GovernedParticipant {
    @SerializedName("userGuid")
    String userGuid;
    
    @SerializedName("alias")
    String alias;
    
    @SerializedName("userProfile")
    Profile userProfile;

    public GovernedParticipant(final String userGuid, final String alias, final UserProfile userProfile) {
        this.userGuid = userGuid;
        this.alias = alias;
        this.userProfile = Optional.ofNullable(userProfile).map(Profile::new).orElse(null);
    }
}
