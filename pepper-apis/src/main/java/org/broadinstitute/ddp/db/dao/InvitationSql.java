package org.broadinstitute.ddp.db.dao;

import java.time.Instant;

import org.broadinstitute.ddp.model.invitation.InvitationType;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface InvitationSql extends SqlObject {

    @GetGeneratedKeys
    @SqlUpdate("insert into invitation (invitation_type_id, invitation_guid, study_id, user_id, created_at, contact_email)"
            + " select invitation_type_id, :guid, :studyId, :userId, :createdAt, :email"
            + "   from invitation_type where invitation_type_code = :type")
    long insertInvitation(
            @Bind("type") InvitationType invitationType,
            @Bind("guid") String guid,
            @Bind("studyId") long studyId,
            @Bind("userId") Long userId,
            @Bind("createdAt") Instant createdAt,
            @Bind("email") String email);

    @SqlUpdate("update invitation set voided_at = :voidedAt where invitation_id = :id")
    int updateVoidedAt(@Bind("id") long invitationId, @Bind("voidedAt") Instant voidedAt);

    @SqlUpdate("update invitation set verified_at = :verifiedAt where invitation_id = :id")
    int updateVerifiedAt(@Bind("id") long invitationId, @Bind("verifiedAt") Instant verifiedAt);

    @SqlUpdate("update invitation set accepted_at = :acceptedAt where invitation_id = :id")
    int updateAcceptedAt(@Bind("id") long invitationId, @Bind("acceptedAt") Instant acceptedAt);

    @SqlUpdate("update invitation set user_id = :userId, accepted_at = :acceptedAt where invitation_id = :id")
    int updateAcceptingUser(@Bind("id") long invitationId, @Bind("userId") long userId, @Bind("acceptedAt") Instant acceptedAt);

    @SqlUpdate("update invitation set voided_at = :voidedAt where study_id = :studyId and user_id = :userId")
    int bulkUpdateVoidedAt(@Bind("studyId") long studyId, @Bind("userId") long userId, @Bind("voidedAt") Instant voidedAt);

    /**
     * Sets nullable date columns to null for the given invitation.  Convenience for testing.
     */
    @SqlUpdate("update invitation set accepted_at = null, voided_at = null, verified_at = null"
            + "  where invitation_id = :id")
    int clearDates(@Bind("id") long invitationId);

    @SqlUpdate("delete from invitation where invitation_id = :id")
    int deleteById(@Bind("id") long invitationId);
}
