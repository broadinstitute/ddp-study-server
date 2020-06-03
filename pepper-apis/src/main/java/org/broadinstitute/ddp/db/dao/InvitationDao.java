package org.broadinstitute.ddp.db.dao;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dto.InvitationDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

public interface InvitationDao extends SqlObject {

    @SqlQuery("select i.*, it.invitation_type_code from invitation as i"
            + "  join invitation_type as it on i.invitation_type_id = it.invitation_type_id"
            + " where i.study_id = :studyId and i.user_id = :userId")
    @RegisterConstructorMapper(InvitationDto.class)
    List<InvitationDto> findInvitations(@Bind("studyId") long studyId, @Bind("userId") long userId);

    @SqlQuery("select i.*, it.invitation_type_code from invitation as i"
            + "  join invitation_type as it on i.invitation_type_id = it.invitation_type_id"
            + "  join umbrella_study as s on s.umbrella_study_id = i.study_id"
            + "  left join user as u on u.user_id = i.user_id"
            + " where s.guid = :studyGuid and u.guid = :userGuid")
    @RegisterConstructorMapper(InvitationDto.class)
    List<InvitationDto> findInvitations(@Bind("studyGuid") String studyGuid, @Bind("userGuid") String userGuid);

    @SqlQuery("select i.*, it.invitation_type_code from invitation as i"
            + "  join invitation_type as it on i.invitation_type_id = it.invitation_type_id"
            + " where i.study_id = :studyId and i.invitation_guid = :guid")
    @RegisterConstructorMapper(InvitationDto.class)
    Optional<InvitationDto> findByInvitationGuid(@Bind("studyId") long studyId, @Bind("guid") String invitationGuid);

    @SqlQuery("select i.*, it.invitation_type_code from invitation as i"
            + "  join invitation_type as it on i.invitation_type_id = it.invitation_type_id"
            + " where i.study_id = :studyId")
    @RegisterConstructorMapper(InvitationDto.class)
    List<InvitationDto> findAllInvitations(@Bind("studyId") long studyId);

    default void saveNotes(long invitationId, String notes) {
        DBUtils.checkUpdate(1, getHandle().attach(InvitationSql.class)
                .updateNotes(invitationId, notes));
    }

    default void markVoided(long invitationId, Instant voidedAt) {
        DBUtils.checkUpdate(1, getHandle().attach(InvitationSql.class)
                .updateVoidedAt(invitationId, voidedAt));
    }

    default void markVerified(long invitationId, Instant verifiedAt) {
        DBUtils.checkUpdate(1, getHandle().attach(InvitationSql.class)
                .updateVerifiedAt(invitationId, verifiedAt));
    }

    default void markAccepted(long invitationId, Instant acceptedAt) {
        DBUtils.checkUpdate(1, getHandle().attach(InvitationSql.class)
                .updateAcceptedAt(invitationId, acceptedAt));
    }

    default void assignAcceptingUser(long invitationId, long userId, Instant acceptedAt) {
        DBUtils.checkUpdate(1, getHandle().attach(InvitationSql.class)
                .updateAcceptingUser(invitationId, userId, acceptedAt));
    }

    default int bulkMarkVoided(long studyId, long userId, Instant voidedAt) {
        return getHandle().attach(InvitationSql.class).bulkUpdateVoidedAt(studyId, userId, voidedAt);
    }
}
