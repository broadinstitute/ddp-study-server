package org.broadinstitute.ddp.db.dto;

import java.time.Instant;

import org.broadinstitute.ddp.model.invitation.InvitationType;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class InvitationDto {

    // don't set this in client code--it will be generated safely on insert
    private String invitationGuid;

    // auto generated
    private long invitationId;
    private InvitationType invitationType;
    private Instant createdAt;
    private Instant voidedAt;
    private Instant verifiedAt;
    private Instant acceptedAt;
    private long studyId;
    private Long userId;
    private String contactEmail;
    private String notes;

    /**
     * This should only be used by JDBI on-the-fly.
     * Please use {@link org.broadinstitute.ddp.db.dao.InvitationFactory#createAgeUpInvitation(long, long, String)} to
     * safely create a new one
     */
    @JdbiConstructor
    public InvitationDto(
            @ColumnName("invitation_id") long invitationId,
            @ColumnName("invitation_guid") String invitationGuid,
            @ColumnName("invitation_type_code") InvitationType invitationType,
            @ColumnName("created_at") Instant createdAt,
            @ColumnName("voided_at") Instant voidedAt,
            @ColumnName("verified_at") Instant verifiedAt,
            @ColumnName("accepted_at") Instant acceptedAt,
            @ColumnName("study_id") long studyId,
            @ColumnName("user_id") Long userId,
            @ColumnName("contact_email") String email,
            @ColumnName("notes") String notes) {
        this.invitationId = invitationId;
        this.invitationGuid = invitationGuid;
        this.invitationType = invitationType;
        this.createdAt = createdAt;
        this.voidedAt = voidedAt;
        this.verifiedAt = verifiedAt;
        this.acceptedAt = acceptedAt;
        this.studyId = studyId;
        this.userId = userId;
        this.contactEmail = email;
        this.notes = notes;
    }

    public long getInvitationId() {
        return invitationId;
    }

    public String getInvitationGuid() {
        return invitationGuid;
    }

    public InvitationType getInvitationType() {
        return invitationType;
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

    public long getStudyId() {
        return studyId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public String getNotes() {
        return notes;
    }

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
