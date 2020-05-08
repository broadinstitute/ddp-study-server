package org.broadinstitute.ddp.json.invitation;

import javax.validation.constraints.NotEmpty;

import com.google.gson.annotations.SerializedName;

public class VerifyInvitationPayload {

    @NotEmpty
    @SerializedName("invitationId")
    private String invitationId;

    public VerifyInvitationPayload(String invitationId) {
        this.invitationId = invitationId;
    }

    public String getInvitationId() {
        return invitationId;
    }
}
