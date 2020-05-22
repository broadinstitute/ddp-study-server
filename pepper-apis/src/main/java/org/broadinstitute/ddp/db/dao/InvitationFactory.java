package org.broadinstitute.ddp.db.dao;

import java.time.Instant;

import org.broadinstitute.ddp.constants.SqlConstants;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dto.InvitationDto;
import org.broadinstitute.ddp.model.invitation.InvitationType;
import org.jdbi.v3.sqlobject.SqlObject;

public interface InvitationFactory extends SqlObject {

    /**
     * Creates a new Age Up {@link InvitationDto invitation}, writing a new row into the database
     * using the current time as creation time and generating a new guid.
     */
    default InvitationDto createAgeUpInvitation(long studyId, long userId, String email) {
        String guid = generateGuid();
        return createInvitation(InvitationType.AGE_UP, guid, studyId, userId, email);
    }

    default InvitationDto createRecruitmentInvitation(long studyId, String guid) {
        return createInvitation(InvitationType.RECRUITMENT, guid, studyId, null, null);
    }

    private InvitationDto createInvitation(InvitationType invitationType, String guid, long studyId, Long userId, String email) {
        Instant createdAt = Instant.now();
        long invitationId = getHandle().attach(InvitationSql.class)
                .insertInvitation(invitationType, guid, studyId, userId, email, null, createdAt);
        return new InvitationDto(invitationId, guid, invitationType, createdAt, null, null, null, studyId, userId, email, null);
    }

    /**
     * Generates a new UUID4 for a new invitation
     * and checks the database to ensure uniqueness.
     */
    default String generateGuid() {
        return DBUtils.uniqueUUID4(getHandle(), SqlConstants.InvitationTable._NAME, SqlConstants.InvitationTable.GUID);
    }
}
