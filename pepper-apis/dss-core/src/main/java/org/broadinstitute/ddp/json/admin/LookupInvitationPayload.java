package org.broadinstitute.ddp.json.admin;

import javax.validation.constraints.NotEmpty;

import com.google.gson.annotations.SerializedName;

public class LookupInvitationPayload {

    @NotEmpty
    @SerializedName("invitationId")
    private String invitationGuid;

    public LookupInvitationPayload(String invitationGuid) {
        this.invitationGuid = invitationGuid;
    }

    public String getInvitationGuid() {
        return invitationGuid;
    }
}
