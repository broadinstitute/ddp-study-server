package org.broadinstitute.ddp.json;

import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.model.user.UserProfile;

import com.google.gson.annotations.SerializedName;

import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class UserCreationResponse {

    @NonNull
    @SerializedName("guid")
    private String guid;

    @NonNull
    @SerializedName("hruid")
    private String hruid;

    @NonNull
    @SerializedName("email")
    private String email;
    
    @SerializedName("profile")
    private Profile profile;

    @SerializedName("center")
    private String center;
    
    public UserCreationResponse(User user, UserProfile profile) {
        this.guid = user.getGuid();
        this.hruid = user.getHruid();
        this.email = user.getEmail().orElseThrow(() -> new IllegalStateException("participant must have a valid email"));
        this.profile = new Profile(profile);
    }
}
