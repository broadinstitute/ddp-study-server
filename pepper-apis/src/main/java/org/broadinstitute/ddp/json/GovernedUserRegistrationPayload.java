package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;

/**
 * Payload sent during governed user registration.
 */

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

    @SerializedName("alias")
    private String alias;

    public GovernedUserRegistrationPayload() {}

    public GovernedUserRegistrationPayload(String languageCode, String firstName, String lastName, String timeZone, String alias) {
        this.languageCode = languageCode;
        this.firstName = firstName;
        this.lastName = lastName;
        this.timeZone = timeZone;
        this.alias = alias;
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

    public String getAlias() {
        return alias;
    }
}
