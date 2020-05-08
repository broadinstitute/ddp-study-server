package org.broadinstitute.ddp.db.dao;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.db.dto.InvitationDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;


public interface InvitationDao extends SqlObject {

    @SqlQuery("select i.*, it.invitation_type_code from invitation as i"
            + "  join invitation_type as it on i.invitation_type_id = it.invitation_type_id"
            + " where i.study_id = :studyId and i.user_id = :userId")
    @RegisterConstructorMapper(InvitationDto.class)
    List<InvitationDto> findInvitations(@Bind("studyId") long studyId, @Bind("userId") long userId);

    @SqlQuery("select i.*, it.invitation_type_code from invitation as i"
            + "  join invitation_type as it on i.invitation_type_id = it.invitation_type_id"
            + " where i.study_id = :studyId and i.invitation_guid = :guid")
    @RegisterConstructorMapper(InvitationDto.class)
    Optional<InvitationDto> findByInvitationGuid(@Bind("studyId") long studyId, @Bind("guid") String invitationGuid);

    @SqlUpdate("update invitation set user_id = :userId, accepted_at = :acceptedAt where invitation_id = :id")
    int assignAcceptingUser(@Bind("id") long invitationId, @Bind("userId") long userId, @Bind("acceptedAt") Instant acceptedAt);

    @SqlUpdate("update invitation set voided_at = :voidedAt where invitation_id = :id")
    int updateVoidedAt(@Bind("id") long invitationId, @Bind("voidedAt") Instant voidedAt);

    @SqlUpdate("update invitation set verified_at = :verifiedAt where invitation_id = :id")
    int updateVerifiedAt(@Bind("id") long invitationId, @Bind("verifiedAt") Instant verifiedAt);

    @SqlUpdate("update invitation set accepted_at = :acceptedAt where invitation_id = :id")
    int updateAcceptedAt(@Bind("id") long invitationId, @Bind("acceptedAt") Instant acceptedAt);

    @SqlUpdate("update invitation set voided_at = :voidedAt where study_id = :studyId and user_id = :userId")
    int bulkUpdateVoidedAt(@Bind("studyId") long studyId, @Bind("userId") long userId, @Bind("voidedAt") Instant voidedAt);

    /**
     * Sets nullable date columns to null for the given invitation.  Convenience for testing.
     */
    @SqlUpdate("update invitation set accepted_at = null, voided_at = null, verified_at = null "
             + "where invitation_guid = :guid")
    int clearDates(@Bind("guid") String invitationGuid);
}
