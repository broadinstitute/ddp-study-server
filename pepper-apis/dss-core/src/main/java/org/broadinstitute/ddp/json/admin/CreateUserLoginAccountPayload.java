package org.broadinstitute.ddp.json.admin;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;

import com.google.gson.annotations.SerializedName;

public class CreateUserLoginAccountPayload {

    @Email
    @NotBlank
    @SerializedName("email")
    private String email;

    public CreateUserLoginAccountPayload(String email) {
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
