package org.broadinstitute.ddp.json.admin;

import javax.annotation.Nullable;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import org.broadinstitute.ddp.db.dto.InvitationDto;
import org.broadinstitute.ddp.json.invitation.Invitation;

@Getter
public final class LookupInvitationResponse extends Invitation {
    @SerializedName("userGuid")
    private String userGuid;

    @SerializedName("userLoginEmail")
    private String userLoginEmail;

    @SerializedName("notes")
    private String notes;

    public LookupInvitationResponse(InvitationDto invitationDto, @Nullable String userGuid, @Nullable String userLoginEmail) {
        super(invitationDto);

        this.notes = invitationDto.getNotes();
        this.userGuid = userGuid;
        this.userLoginEmail = userLoginEmail;
    }
}
