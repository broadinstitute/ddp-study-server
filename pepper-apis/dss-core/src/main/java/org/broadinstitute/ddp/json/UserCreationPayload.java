package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;

import java.time.LocalDate;

/**
 * Payload sent during creation of a new user
 */
@Data
@AllArgsConstructor
public class UserCreationPayload {
    @NonNull
    @SerializedName("email")
    private String email;

    @NonNull
    @SerializedName("studyGuid")
    private String studyGuid;

    @NonNull
    @SerializedName("firstName")
    private String firstName;

    @NonNull
    @SerializedName("lastName")
    private String lastName;

    @NonNull
    @SerializedName("birthDate")
    private LocalDate birthDate;

    @SerializedName("informedConsentDate")
    private LocalDate informedConsentDate;

    @SerializedName("assentDate")
    private LocalDate assentDate;

    @SerializedName("centerId")
    private String centerId;
}
