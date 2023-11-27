package org.broadinstitute.ddp.json;

import javax.validation.constraints.NotBlank;

import com.google.gson.annotations.SerializedName;
import org.apache.commons.lang3.StringUtils;

/**
 * Payload sent during registration of a new user or login of an existing user.
 */
public class UserRegistrationPayload {

    // Required properties

    @NotBlank
    @SerializedName("auth0ClientId")
    private String auth0ClientId;

    @NotBlank
    @SerializedName("studyGuid")
    private String studyGuid;

    // Required during normal registration

    @SerializedName("auth0Domain")
    private String auth0Domain;

    @SerializedName("auth0UserId")
    private String auth0UserId;

    // Required during local registration

    @SerializedName("auth0Code")
    private String auth0Code; // only set during local dev registration

    @SerializedName("redirectUri")
    private String redirectUri; // only set during local dev registration

    // Optional properties

    @SerializedName("tempUserGuid")
    private String tempUserGuid;

    @SerializedName("mode")
    private String mode;

    @SerializedName("invitationId")
    private String invitationGuid;

    @SerializedName("languageCode")
    private String languageCode;

    @SerializedName("firstName")
    private String firstName;

    @SerializedName("lastName")
    private String lastName;

    @SerializedName("timeZone")
    private String timeZone;

    public UserRegistrationPayload(String auth0UserId, String auth0ClientId,
                                   String studyGuid, String auth0Domain, String tempUserGuid, String mode) {
        this(auth0UserId, auth0ClientId, studyGuid, auth0Domain, tempUserGuid, mode, null);
    }

    public UserRegistrationPayload(String auth0UserId, String auth0ClientId,
                                   String studyGuid, String auth0Domain, String tempUserGuid, String mode,
                                   String invitationGuid) {
        this(auth0UserId, auth0ClientId, studyGuid, auth0Domain, invitationGuid);
        this.tempUserGuid = tempUserGuid;
        this.mode = mode;
    }

    public UserRegistrationPayload(String auth0UserId, String auth0ClientId,
                                   String studyGuid, String auth0Domain) {
        this(auth0UserId, auth0ClientId, studyGuid, auth0Domain, null);
    }

    public UserRegistrationPayload(String auth0UserId, String auth0ClientId,
                                   String studyGuid, String auth0Domain, String invitationGuid) {
        this.auth0UserId = auth0UserId;
        this.auth0ClientId = auth0ClientId;
        this.studyGuid = studyGuid;
        this.auth0Domain = auth0Domain;
        this.invitationGuid = invitationGuid;
    }

    public boolean isLocalRegistration() {
        return StringUtils.isNoneBlank(auth0Code, redirectUri);
    }

    public String getAuth0Code() {
        return auth0Code;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public String getAuth0UserId() {
        return auth0UserId;
    }

    public String getAuth0ClientId() {
        return auth0ClientId;
    }

    public String getStudyGuid() {
        return studyGuid;
    }

    public String getAuth0Domain() {
        return auth0Domain;
    }

    public String getTempUserGuid() {
        return tempUserGuid;
    }

    public String getMode() {
        return mode;
    }

    public String getInvitationGuid() {
        return invitationGuid;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public UserRegistrationPayload setLanguageCode(String languageCode) {
        this.languageCode = languageCode;
        return this;
    }

    public String getFirstName() {
        return firstName;
    }

    public UserRegistrationPayload setFirstName(String firstName) {
        this.firstName = firstName;
        return this;
    }

    public String getLastName() {
        return lastName;
    }

    public UserRegistrationPayload setLastName(String lastName) {
        this.lastName = lastName;
        return this;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public UserRegistrationPayload setTimeZone(String timeZone) {
        this.timeZone = timeZone;
        return this;
    }
}
