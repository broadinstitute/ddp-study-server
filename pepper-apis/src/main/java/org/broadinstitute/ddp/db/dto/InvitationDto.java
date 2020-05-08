package org.broadinstitute.ddp.db.dto;

import java.time.Instant;

import org.broadinstitute.ddp.model.invitation.InvitationType;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;


public class InvitationDto {

    private String contactEmail;
    // don't set this in client code--it will be generated safely on insert
    private String invitationGuid;

    // auto generated
    private long invitationId;

    private Instant createdAt;

    private Instant voidedAt;

    private Instant verifiedAt;

    private Instant acceptedAt;

    private long studyId;

    private long userId;

    private InvitationType invitationType;

    /**
     * This should only be used by JDBI on-the-fly.
     * Please use {@link org.broadinstitute.ddp.db.dao.InvitationFactory#createInvitation(InvitationType, long, long, String)} to
     * safely create a new one
     */
    @JdbiConstructor
    public InvitationDto(@ColumnName("invitation_guid") String invitationGuid,
                         @ColumnName("invitation_id") long invitationId,
                         @ColumnName("created_at") Instant createdAt,
                         @ColumnName("voided_at") Instant voidedAt,
                         @ColumnName("verified_at") Instant verifiedAt,
                         @ColumnName("accepted_at") Instant acceptedAt,
                         @ColumnName("study_id") long studyId,
                         @ColumnName("user_id") long userId,
                         @ColumnName("invitation_type_code") InvitationType invitationType,
                         @ColumnName("contact_email") String email) {
        this.invitationId = invitationId;
        this.invitationGuid = invitationGuid;
        this.createdAt = createdAt;
        this.voidedAt = voidedAt;
        this.verifiedAt = verifiedAt;
        this.acceptedAt = acceptedAt;
        this.studyId = studyId;
        this.userId = userId;
        this.invitationType = invitationType;
        this.contactEmail = email;
    }

    public long getInvitationId() {
        return invitationId;
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

    public long getStudyId() {
        return studyId;
    }

    public long getUserId() {
        return userId;
    }

    public InvitationType getInvitationType() {
        return invitationType;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public boolean isVoid() {
        return voidedAt != null;
    }

    /**
     * Returns true if this invitation hasn't
     * been voided or verified
     */
    public boolean canBeVerified() {
        return !isVoid() && !isVerified();
    }

    private boolean isVerified() {
        return verifiedAt != null;
    }

    public boolean isAccepted() {
        return acceptedAt != null;
    }
}
