package org.broadinstitute.ddp.json;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class SendEmailPayload {
    @Email
    @NotNull
    @SerializedName("email")
    String email;
}
