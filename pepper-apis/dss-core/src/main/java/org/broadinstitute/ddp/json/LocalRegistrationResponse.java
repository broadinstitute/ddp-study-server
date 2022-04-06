package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.broadinstitute.ddp.util.Auth0Util;

@Value
@AllArgsConstructor
public class LocalRegistrationResponse {
    @SerializedName("idToken")
    String idToken;

    @SerializedName("accessToken")
    String accessToken;
    
    public LocalRegistrationResponse(final Auth0Util.RefreshTokenResponse refreshResponse) {
        this(refreshResponse.getIdToken(), refreshResponse.getAccessToken());
    }
}
