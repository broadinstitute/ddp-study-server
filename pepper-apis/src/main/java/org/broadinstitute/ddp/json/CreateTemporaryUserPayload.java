package org.broadinstitute.ddp.json;

import javax.validation.constraints.NotBlank;

import com.google.gson.annotations.SerializedName;

public class CreateTemporaryUserPayload {

    @NotBlank
    @SerializedName("auth0ClientId")
    private String auth0ClientId;

    public CreateTemporaryUserPayload(String auth0ClientId) {
        this.auth0ClientId = auth0ClientId;
    }

    public String getAuth0ClientId() {
        return auth0ClientId;
    }
}
