package org.broadinstitute.ddp.db.dao;

import java.sql.Timestamp;
import java.util.Optional;

import org.broadinstitute.ddp.db.dto.InvitationDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;


public interface InvitationDao extends SqlObject {

    @SqlQuery("select i.*, it.invitation_type_code from invitation i, invitation_type it "
            + "where i.invitation_type_id = it.invitation_type_id "
            + "and i.invitation_guid = :guid")
    @RegisterConstructorMapper(InvitationDto.class)
    Optional<InvitationDto> findByInvitationGuid(@Bind("guid") String invitationGuid);

    @SqlUpdate("update invitation set voided_at = :voidedAt where invitation_guid = :invitationGuid")
    int updateVoidedAt(@Bind("voidedAt") Timestamp voidedAt, @Bind("invitationGuid") String invitationGuid);

    @SqlUpdate("update invitation set verified_at = :verifiedAt where invitation_guid = :invitationGuid")
    int updateVerifiedAt(@Bind("verifiedAt") Timestamp verifiedAt, @Bind("invitationGuid") String invitationGuid);

    @SqlUpdate("update invitation set accepted_at = :acceptedAt where invitation_guid = :invitationGuid")
    int updateAcceptedAt(@Bind("acceptedAt") Timestamp acceptedAt, @Bind("invitationGuid") String invitationGuid);

    /**
     * Sets nullable date columns to null for the given invitation.  Convenience for testing.
     */
    @SqlUpdate("update invitation set accepted_at = null, voided_at = null, verified_at = null "
             + "where invitation_guid = :guid")
    int clearDates(@Bind("guid") String invitationGuid);
}
