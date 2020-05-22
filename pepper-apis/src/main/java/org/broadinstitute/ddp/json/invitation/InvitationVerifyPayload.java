package org.broadinstitute.ddp.json.invitation;

import javax.validation.constraints.NotEmpty;

import com.google.gson.annotations.SerializedName;

public class InvitationVerifyPayload {

    @NotEmpty
    @SerializedName("invitationId")
    private String invitationGuid;

    public InvitationVerifyPayload(String invitationGuid) {
        this.invitationGuid = invitationGuid;
    }

    public String getInvitationGuid() {
        return invitationGuid;
    }
}
