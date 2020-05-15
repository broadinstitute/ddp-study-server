package org.broadinstitute.ddp.json;

import javax.validation.constraints.NotEmpty;

import com.google.gson.annotations.SerializedName;

public class InvitationLookupPayload {

    @NotEmpty
    @SerializedName("invitationId")
    private String invitationGuid;

    public InvitationLookupPayload(String invitationGuid) {
        this.invitationGuid = invitationGuid;
    }

    public String getInvitationGuid() {
        return invitationGuid;
    }
}
