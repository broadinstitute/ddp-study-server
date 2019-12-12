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

    @SerializedName("auth0ClientCountryCode")
    private String auth0ClientCountryCode;

    @SerializedName("mode")
    private String mode;

    public UserRegistrationPayload(String auth0UserId, String auth0ClientId, String auth0ClientCountryCode,
                                   String studyGuid, String auth0Domain, String tempUserGuid, String mode) {
        this(auth0UserId, auth0ClientId, auth0ClientCountryCode, studyGuid, auth0Domain);
        this.tempUserGuid = tempUserGuid;
        this.mode = mode;
    }

    public UserRegistrationPayload(String auth0UserId, String auth0ClientId, String auth0ClientCountryCode,
                                   String studyGuid, String auth0Domain) {
        this.auth0UserId = auth0UserId;
        this.auth0ClientId = auth0ClientId;
        this.auth0ClientCountryCode = auth0ClientCountryCode;
        this.studyGuid = studyGuid;
        this.auth0Domain = auth0Domain;
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

    public String getAuth0ClientCountryCode() {
        return auth0ClientCountryCode;
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
}
