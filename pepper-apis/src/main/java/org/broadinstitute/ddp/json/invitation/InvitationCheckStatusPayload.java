package org.broadinstitute.ddp.json.invitation;

import javax.validation.constraints.NotEmpty;

import com.google.gson.annotations.SerializedName;

public class InvitationCheckStatusPayload {

    @NotEmpty
    @SerializedName("auth0Domain")
    private String auth0Domain;

    @NotEmpty
    @SerializedName("auth0ClientId")
    private String auth0ClientId;

    @NotEmpty
    @SerializedName("invitationId")
    private String invitationGuid;

    // @NotEmpty
    // @SerializedName("recaptchaToken")
    // private String recaptchaToken;

    public InvitationCheckStatusPayload(String auth0Domain, String auth0ClientId, String invitationGuid) {
        this.auth0Domain = auth0Domain;
        this.auth0ClientId = auth0ClientId;
        this.invitationGuid = invitationGuid;
    }

    public String getAuth0Domain() {
        return auth0Domain;
    }

    public String getAuth0ClientId() {
        return auth0ClientId;
    }

    public String getInvitationGuid() {
        return invitationGuid;
    }
}
