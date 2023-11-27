package org.broadinstitute.ddp.json.auth0;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;

import com.google.gson.annotations.SerializedName;

public class UpdateUserEmailRequestPayload {
    @SerializedName("email")
    @Email
    @NotBlank
    private String email;

    public UpdateUserEmailRequestPayload(String email) {
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
