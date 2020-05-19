package org.broadinstitute.ddp.json.admin;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;

import com.google.gson.annotations.SerializedName;
import org.hibernate.validator.constraints.URL;

public class CreateUserLoginAccountPayload {

    @NotBlank
    @SerializedName("auth0ClientId")
    private String auth0ClientId;

    @Email
    @NotBlank
    @SerializedName("email")
    private String email;

    @URL
    @NotBlank
    @SerializedName("redirectUrl")
    private String redirectUrl;

    public CreateUserLoginAccountPayload(String auth0ClientId, String email, String redirectUrl) {
        this.auth0ClientId = auth0ClientId;
        this.email = email;
        this.redirectUrl = redirectUrl;
    }

    public String getAuth0ClientId() {
        return auth0ClientId;
    }

    public String getEmail() {
        return email;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }
}
