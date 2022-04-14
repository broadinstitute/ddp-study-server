package org.broadinstitute.ddp.export.json.structured;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.db.dto.InvitationDto;
import org.broadinstitute.ddp.model.invitation.InvitationType;

public class InvitationRecord {

    @SerializedName("type")
    private InvitationType type;
    @SerializedName("guid")
    private String invitationGuid;
    @SerializedName("createdAt")
    private long createdAt;
    @SerializedName("voidedAt")
    private Long voidedAt;
    @SerializedName("verifiedAt")
    private Long verifiedAt;
    @SerializedName("acceptedAt")
    private Long acceptedAt;
    @SerializedName("contactEmail")
    private String contactEmail;
    @SerializedName("notes")
    private String notes;

    public InvitationRecord(InvitationDto invite) {
        this(invite.getInvitationType(),
                invite.getInvitationGuid(),
                invite.getCreatedAt().toEpochMilli(),
                invite.getVoidedAt() != null ? invite.getVoidedAt().toEpochMilli() : null,
                invite.getVerifiedAt() != null ? invite.getVerifiedAt().toEpochMilli() : null,
                invite.getAcceptedAt() != null ? invite.getAcceptedAt().toEpochMilli() : null,
                invite.getContactEmail(),
                invite.getNotes());
    }

    public InvitationRecord(InvitationType type, String invitationGuid,
                            long createdAt, Long voidedAt,
                            Long verifiedAt, Long acceptedAt,
                            String contactEmail, String notes) {
        this.type = type;
        this.invitationGuid = invitationGuid;
        this.createdAt = createdAt;
        this.voidedAt = voidedAt;
        this.verifiedAt = verifiedAt;
        this.acceptedAt = acceptedAt;
        this.contactEmail = contactEmail;
        this.notes = notes;
    }
}
