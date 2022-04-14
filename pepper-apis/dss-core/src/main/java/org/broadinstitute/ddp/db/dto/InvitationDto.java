package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.broadinstitute.ddp.model.invitation.InvitationType;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

import java.time.Instant;

@Value
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class InvitationDto {
    @ColumnName("invitation_id")
    long invitationId;

    @ColumnName("invitation_guid")
    String invitationGuid;

    @ColumnName("invitation_type_code")
    InvitationType invitationType;

    @ColumnName("created_at")
    Instant createdAt;

    @ColumnName("voided_at")
    Instant voidedAt;

    @ColumnName("verified_at")
    Instant verifiedAt;

    @ColumnName("accepted_at")
    Instant acceptedAt;

    @ColumnName("study_id")
    long studyId;

    @ColumnName("user_id")
    Long userId;

    @ColumnName("contact_email")
    String contactEmail;

    @ColumnName("notes")
    String notes;

    public boolean isVoid() {
        return voidedAt != null;
    }

    /**
     * Returns true if this invitation hasn't been voided or verified
     */
    public boolean canBeVerified() {
        return !isVoid() && !isVerified();
    }

    public boolean isVerified() {
        return verifiedAt != null;
    }

    public boolean isAccepted() {
        return acceptedAt != null;
    }
}
