package org.broadinstitute.ddp.db.dto;

import java.sql.Timestamp;

import org.broadinstitute.ddp.model.invitation.InvitationType;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;


public class InvitationDto {

    private String contactEmail;
    // don't set this in client code--it will be generated safely on insert
    private String invitationGuid;

    // auto generated
    private Long invitationId;

    private Timestamp createdAt;

    private Timestamp voidedAt;

    private Timestamp verifiedAt;

    private Timestamp acceptedAt;

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
                         @ColumnName("created_at") Timestamp createdAt,
                         @ColumnName("voided_at") Timestamp voidedAt,
                         @ColumnName("verified_at") Timestamp verifiedAt,
                         @ColumnName("accepted_at") Timestamp acceptedAt,
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

    public Long getInvitationId() {
        return invitationId;
    }

    public String getInvitationGuid() {
        return invitationGuid;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public Timestamp getVoidedAt() {
        return voidedAt;
    }

    public Timestamp getVerifiedAt() {
        return verifiedAt;
    }

    public Timestamp getAcceptedAt() {
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

    public void setCreatedAt(Timestamp createdAt) {
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
