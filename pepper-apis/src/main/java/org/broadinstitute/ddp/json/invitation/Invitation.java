package org.broadinstitute.ddp.json.invitation;

import java.time.Instant;
import javax.annotation.Nullable;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.db.dto.InvitationDto;
import org.broadinstitute.ddp.model.invitation.InvitationType;
import org.broadinstitute.ddp.transformers.InstantToIsoDateTimeUtcStrAdapter;

public class Invitation {

    @SerializedName("invitationType")
    private InvitationType invitationType;

    @SerializedName("invitationId")
    private String invitationGuid;

    @SerializedName("createdAt")
    @JsonAdapter(InstantToIsoDateTimeUtcStrAdapter.class)
    private Instant createdAt;

    @SerializedName("voidedAt")
    @JsonAdapter(InstantToIsoDateTimeUtcStrAdapter.class)
    private Instant voidedAt;

    @SerializedName("verifiedAt")
    @JsonAdapter(InstantToIsoDateTimeUtcStrAdapter.class)
    private Instant verifiedAt;

    @SerializedName("acceptedAt")
    @JsonAdapter(InstantToIsoDateTimeUtcStrAdapter.class)
    private Instant acceptedAt;

    public Invitation(InvitationDto invite) {
        this(invite.getInvitationType(),
                invite.getInvitationGuid(),
                invite.getCreatedAt(),
                invite.getVoidedAt(),
                invite.getVerifiedAt(),
                invite.getAcceptedAt());
    }

    public Invitation(InvitationType invitationType, String invitationGuid, Instant createdAt,
                      @Nullable Instant voidedAt, @Nullable Instant verifiedAt, @Nullable Instant acceptedAt) {
        this.invitationType = invitationType;
        this.invitationGuid = invitationGuid;
        this.createdAt = createdAt;
        this.voidedAt = voidedAt;
        this.verifiedAt = verifiedAt;
        this.acceptedAt = acceptedAt;
    }

    public InvitationType getInvitationType() {
        return invitationType;
    }

    public String getInvitationGuid() {
        return invitationGuid;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getVoidedAt() {
        return voidedAt;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }
}
