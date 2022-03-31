package org.broadinstitute.dsm.model.auth0;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class Auth0M2MResponse {

    @SerializedName("access_token")
    private String accessToken;
    @SerializedName("token_type")
    private String tokenType;
    @SerializedName("expires_in")
    private String expiresIn;

    @SerializedName("error")
    private String error;
    @SerializedName("error_description")
    private String errorDescription;


}
