package org.broadinstitute.ddp.json.users.requests;

import java.time.LocalDate;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Payload sent during creation of a new user
 */
@Data
@AllArgsConstructor
public class UserCreationPayload {
    @NotEmpty
    @Email
    @SerializedName("email")
    private String email;

    @NotEmpty
    @SerializedName("studyGuid")
    private String studyGuid;

    @NotEmpty
    @SerializedName("firstName")
    private String firstName;

    @NotEmpty
    @SerializedName("lastName")
    private String lastName;

    @NotNull
    @SerializedName("birthDate")
    private LocalDate birthDate;

    @NotNull
    @SerializedName("consentDate")
    private LocalDate consentDate;

    @SerializedName("assentDate")
    private LocalDate assentDate;

    @SerializedName("centerId")
    private String centerId;
}
