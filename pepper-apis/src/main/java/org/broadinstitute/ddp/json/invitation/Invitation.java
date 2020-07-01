package org.broadinstitute.ddp.json.invitation;

import java.time.Instant;

import javax.annotation.Nullable;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.db.dto.InvitationDto;
import org.broadinstitute.ddp.model.invitation.InvitationType;

public class Invitation {

    @SerializedName("invitationType")
    private InvitationType invitationType;
    @SerializedName("invitationId")
    private String invitationGuid;
    @SerializedName("createdAt")
    private String createdAt;
    @SerializedName("voidedAt")
    private String voidedAt;
    @SerializedName("verifiedAt")
    private String verifiedAt;
    @SerializedName("acceptedAt")
    private String acceptedAt;
    @SerializedName("notes")
    private String notes;

    public Invitation(InvitationDto invite) {
        this(invite.getInvitationType(),
                invite.getInvitationGuid(),
                invite.getCreatedAt(),
                invite.getVoidedAt(),
                invite.getVerifiedAt(),
                invite.getAcceptedAt(),
                invite.getNotes());
    }

    public Invitation(InvitationType invitationType, String invitationGuid, Instant createdAt,
                      @Nullable Instant voidedAt, @Nullable Instant verifiedAt, @Nullable Instant acceptedAt,
                      @Nullable String notes) {
        this.invitationType = invitationType;
        this.invitationGuid = invitationGuid;
        this.createdAt = createdAt.toString();
        this.voidedAt = voidedAt != null ? voidedAt.toString() : null;
        this.verifiedAt = verifiedAt != null ? verifiedAt.toString() : null;
        this.acceptedAt = acceptedAt != null ? acceptedAt.toString() : null;
        this.notes = notes;
    }

    public InvitationType getInvitationType() {
        return invitationType;
    }

    public String getInvitationGuid() {
        return invitationGuid;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getVoidedAt() {
        return voidedAt;
    }

    public String getVerifiedAt() {
        return verifiedAt;
    }

    public String getAcceptedAt() {
        return acceptedAt;
    }

    public String getNotes() {
        return notes;
    }
}
