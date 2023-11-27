package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface StudySql extends SqlObject {

    //
    // Study settings
    //

    @SqlUpdate("insert into study_settings (umbrella_study_id, invite_error_template_id, analytics_enabled, analytics_token,"
            + "        should_delete_unsendable_emails, should_display_language_change_popup)"
            + " values (:studyId, :inviteErrorTmplId, :analyticsEnabled, :analyticsToken, "
            + ":shouldDeleteUnsendableEmails, :shouldDisplayLanguageChangePopup)")
    int insertSettings(
            @Bind("studyId") long studyId,
            @Bind("inviteErrorTmplId") Long inviteErrorTemplateId,
            @Bind("analyticsEnabled") boolean analyticsEnabled,
            @Bind("analyticsToken") String analyticsToken,
            @Bind("shouldDeleteUnsendableEmails") boolean shouldDeleteUnsendableEmails,
            @Bind("shouldDisplayLanguageChangePopup") boolean shouldDisplayLanguageChangePopup);

    @SqlUpdate("delete from study_settings where umbrella_study_id = :studyId")
    int deleteSettings(@Bind("studyId") long studyId);
}
