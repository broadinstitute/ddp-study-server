package org.broadinstitute.ddp.json;

import javax.validation.constraints.NotBlank;

import com.google.gson.annotations.SerializedName;

public class CreateTemporaryUserPayload {

    @NotBlank
    @SerializedName("auth0ClientId")
    private String auth0ClientId;

    @SerializedName("auth0Domain")
    private String auth0Domain;

    public CreateTemporaryUserPayload(String auth0ClientId, String auth0Domain) {
        this.auth0ClientId = auth0ClientId;
        this.auth0Domain = auth0Domain;
    }

    public String getAuth0ClientId() {
        return auth0ClientId;
    }

    public String getAuth0Domain() {
        return auth0Domain;
    }
}
