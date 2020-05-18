package org.broadinstitute.ddp.json;

import javax.validation.constraints.NotBlank;

import com.google.gson.annotations.SerializedName;

public class CreateStudyParticipantPayload {

    @NotBlank
    @SerializedName("invitationId")
    private String invitationGuid;

    public CreateStudyParticipantPayload(String invitationGuid) {
        this.invitationGuid = invitationGuid;
    }

    public String getInvitationGuid() {
        return invitationGuid;
    }
}
