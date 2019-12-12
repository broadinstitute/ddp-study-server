package org.broadinstitute.ddp.json;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;

public class SendEmailPayload {

    @Email
    @NotNull
    @SerializedName("email")
    private String email;

    public SendEmailPayload(String email) {
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
