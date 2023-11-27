package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GovernedUserRegistrationPayload {
    // Optional properties
    @SerializedName("languageCode")
    private String languageCode;

    @SerializedName("firstName")
    private String firstName;

    @SerializedName("lastName")
    private String lastName;

    @SerializedName("timeZone")
    private String timeZone;

    public GovernedUserRegistrationPayload setLanguageCode(String languageCode) {
        this.languageCode = languageCode;
        return this;
    }

    public GovernedUserRegistrationPayload setFirstName(String firstName) {
        this.firstName = firstName;
        return this;
    }

    public GovernedUserRegistrationPayload setLastName(String lastName) {
        this.lastName = lastName;
        return this;
    }

    public GovernedUserRegistrationPayload setTimeZone(String timeZone) {
        this.timeZone = timeZone;
        return this;
    }
}
