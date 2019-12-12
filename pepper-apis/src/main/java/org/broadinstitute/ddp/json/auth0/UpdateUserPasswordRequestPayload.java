package org.broadinstitute.ddp.json.auth0;

import javax.validation.constraints.NotBlank;

import com.google.gson.annotations.SerializedName;

public class UpdateUserPasswordRequestPayload {
    @SerializedName("password")
    @NotBlank
    private String password;

    @SerializedName("currentPassword")
    @NotBlank
    private String currentPassword;

    public UpdateUserPasswordRequestPayload(String currentPassword, String password) {
        this.currentPassword = currentPassword;
        this.password = password;
    }

    public String getPassword() {
        return password;
    }

    public String getCurrentPassword() {
        return currentPassword;
    }

}
