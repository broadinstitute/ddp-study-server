package org.broadinstitute.ddp.json.invitation;

import java.time.Instant;
import javax.annotation.Nullable;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.broadinstitute.ddp.db.dto.InvitationDto;
import org.broadinstitute.ddp.model.invitation.InvitationType;
import org.broadinstitute.ddp.transformers.InstantToIsoDateTimeUtcStrAdapter;

@Getter
@AllArgsConstructor
public class Invitation {
    @SerializedName("invitationType")
    private final InvitationType invitationType;

    @SerializedName("invitationId")
    private final String invitationGuid;

    @SerializedName("createdAt")
    @JsonAdapter(InstantToIsoDateTimeUtcStrAdapter.class)
    private final Instant createdAt;

    @Nullable
    @SerializedName("voidedAt")
    @JsonAdapter(InstantToIsoDateTimeUtcStrAdapter.class)
    private final Instant voidedAt;

    @Nullable
    @SerializedName("verifiedAt")
    @JsonAdapter(InstantToIsoDateTimeUtcStrAdapter.class)
    private final Instant verifiedAt;

    @Nullable
    @SerializedName("acceptedAt")
    @JsonAdapter(InstantToIsoDateTimeUtcStrAdapter.class)
    private final Instant acceptedAt;

    public Invitation(final InvitationDto invite) {
        this(invite.getInvitationType(),
                invite.getInvitationGuid(),
                invite.getCreatedAt(),
                invite.getVoidedAt(),
                invite.getVerifiedAt(),
                invite.getAcceptedAt());
    }
}
