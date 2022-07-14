package org.broadinstitute.ddp.json.users.requests;

import java.time.LocalDate;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Past;
import javax.validation.constraints.PastOrPresent;

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
    @Past
    @SerializedName("birthDate")
    private LocalDate birthDate;

    @NotNull
    @PastOrPresent
    @SerializedName("consentDate")
    private LocalDate consentDate;

    @PastOrPresent
    @SerializedName("assentDate")
    private LocalDate assentDate;

    @SerializedName("centerId")
    private String centerId;
}
