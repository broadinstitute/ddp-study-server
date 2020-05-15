package org.broadinstitute.ddp.json;

import javax.validation.constraints.NotEmpty;

import com.google.gson.annotations.SerializedName;

public class InvitationUpdateDetailsPayload {

    @NotEmpty
    @SerializedName("invitationId")
    private String invitationGuid;

    @SerializedName("notes")
    private String notes;

    public InvitationUpdateDetailsPayload(String invitationGuid, String notes) {
        this.invitationGuid = invitationGuid;
        this.notes = notes;
    }

    public String getInvitationGuid() {
        return invitationGuid;
    }

    public String getNotes() {
        return notes;
    }
}
