package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface StudySql extends SqlObject {

    //
    // Study settings
    //

    @SqlUpdate("insert into study_settings (umbrella_study_id, invite_error_template_id)"
            + " values (:studyId, :inviteErrorTmplId)")
    int insertSettings(
            @Bind("studyId") long studyId,
            @Bind("inviteErrorTmplId") Long inviteErrorTemplateId);

    @SqlUpdate("insert into study_settings (umbrella_study_id, invite_error_template_id, analytics_enabled, analytics_token)"
            + " values (:studyId, :inviteErrorTmplId, :analyticsEnabled, :analyticsToken)")
    int insertSettings(
            @Bind("studyId") long studyId,
            @Bind("inviteErrorTmplId") Long inviteErrorTemplateId,
            @Bind("analytics_enabled") boolean analyticsEnabled,
            @Bind("analytics_token") String analyticsToken);

    @SqlUpdate("delete from study_settings where umbrella_study_id = :studyId")
    int deleteSettings(@Bind("studyId") long studyId);
}
