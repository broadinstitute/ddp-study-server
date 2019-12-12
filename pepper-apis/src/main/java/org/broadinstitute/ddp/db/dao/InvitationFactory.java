package org.broadinstitute.ddp.db.dao;

import java.sql.Timestamp;

import org.broadinstitute.ddp.constants.SqlConstants;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dto.InvitationDto;
import org.broadinstitute.ddp.model.invitation.InvitationType;
import org.broadinstitute.ddp.util.TimestampUtil;
import org.jdbi.v3.sqlobject.SqlObject;

public interface InvitationFactory extends SqlObject {

    /**
     * Creates a new {@link InvitationDto invitation}, writing a new row into the database
     * using the current time as creation time and generating a new guid.
     */
    default InvitationDto createInvitation(InvitationType invitationType, long studyId, long userId, String email) {
        Timestamp createdAt = TimestampUtil.now();
        String guid = generateGuid();
        long invitationId = getHandle().attach(InvitationSql.class).insertInvitation(invitationType, guid, studyId, userId, createdAt,
                email);

        return new InvitationDto(guid, invitationId, createdAt, null, null, null, studyId, userId,
                invitationType, email);

    }

    /**
     * Generates a new UUID4 for a new invitation
     * and checks the database to ensure uniqueness.
     */
    default String generateGuid() {
        return DBUtils.uniqueUUID4(getHandle(), SqlConstants.InvitationTable._NAME, SqlConstants.InvitationTable.GUID);
    }
}
