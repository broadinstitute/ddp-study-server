package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.util.Auth0Util;

public class LocalRegistrationResponse {

    @SerializedName("idToken")
    private final String idToken;

    @SerializedName("accessToken")
    private final String accessToken;


    public LocalRegistrationResponse(Auth0Util.RefreshTokenResponse refreshResponse) {
        this(refreshResponse.getIdToken(), refreshResponse.getAccessToken());
    }

    private LocalRegistrationResponse(String idToken, String accessToken) {
        this.idToken = idToken;
        this.accessToken = accessToken;
    }
}
