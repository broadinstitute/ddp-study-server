package org.broadinstitute.ddp.json;

import javax.validation.constraints.NotBlank;

import com.google.gson.annotations.SerializedName;

/**
 * Payload sent during governed user registration.
 */

public class GovernedUserRegistrationPayload {

    // Required properties
    @NotBlank
    @SerializedName("studyGuid")
    private final String studyGuid;

    // Optional properties
    @SerializedName("languageCode")
    private String languageCode;

    @SerializedName("firstName")
    private String firstName;

    @SerializedName("lastName")
    private String lastName;

    @SerializedName("timeZone")
    private String timeZone;

    public GovernedUserRegistrationPayload(String studyGuid) {
        this.studyGuid = studyGuid;
    }

    public String getStudyGuid() {
        return studyGuid;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public GovernedUserRegistrationPayload setLanguageCode(String languageCode) {
        this.languageCode = languageCode;
        return this;
    }

    public String getFirstName() {
        return firstName;
    }

    public GovernedUserRegistrationPayload setFirstName(String firstName) {
        this.firstName = firstName;
        return this;
    }

    public String getLastName() {
        return lastName;
    }

    public GovernedUserRegistrationPayload setLastName(String lastName) {
        this.lastName = lastName;
        return this;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public GovernedUserRegistrationPayload setTimeZone(String timeZone) {
        this.timeZone = timeZone;
        return this;
    }
}
