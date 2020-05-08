package org.broadinstitute.ddp.db.dao;

import java.time.Instant;

import org.broadinstitute.ddp.model.invitation.InvitationType;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface InvitationSql extends SqlObject {

    @SqlUpdate("insert into invitation(invitation_type_id, invitation_guid, study_id, user_id, created_at, contact_email) "
             + "(select invitation_type_id, :guid, :studyId, :userId, :createdAt, :email "
             + "from invitation_type where invitation_type_code = :type)")
    @GetGeneratedKeys
    long insertInvitation(@Bind("type") InvitationType invitationType,
                @Bind("guid") String guid,
                @Bind("studyId") long studyId,
                @Bind("userId") long userId,
                @Bind("createdAt") Instant createdAt,
                @Bind("email") String email);
}
