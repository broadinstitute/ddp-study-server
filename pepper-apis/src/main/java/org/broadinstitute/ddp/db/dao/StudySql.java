package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface StudySql extends SqlObject {

    //
    // Invite settings
    //

    @SqlUpdate("insert into study_invite_setting (umbrella_study_id, invite_error_template_id)"
            + " values (:studyId, :inviteErrorTmplId)")
    int insertInviteSetting(
            @Bind("studyId") long studyId,
            @Bind("inviteErrorTmplId") Long inviteErrorTemplateId);

    @SqlUpdate("delete from study_invite_setting where umbrella_study_id = :studyId")
    int deleteInviteSetting(@Bind("studyId") long studyId);
}
