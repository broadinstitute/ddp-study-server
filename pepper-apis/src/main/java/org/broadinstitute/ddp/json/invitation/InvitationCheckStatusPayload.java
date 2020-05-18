package org.broadinstitute.ddp.json.invitation;

import javax.validation.constraints.NotEmpty;

import com.google.gson.annotations.SerializedName;

public class InvitationCheckStatusPayload {

    @NotEmpty
    @SerializedName("auth0ClientId")
    private String auth0ClientId;

    @NotEmpty
    @SerializedName("invitationId")
    private String invitationGuid;

    // @NotEmpty
    // @SerializedName("recaptchaToken")
    // private String recaptchaToken;

    public InvitationCheckStatusPayload(String auth0ClientId, String invitationGuid) {
        this.auth0ClientId = auth0ClientId;
        this.invitationGuid = invitationGuid;
    }

    public String getAuth0ClientId() {
        return auth0ClientId;
    }

    public String getInvitationGuid() {
        return invitationGuid;
    }
}
