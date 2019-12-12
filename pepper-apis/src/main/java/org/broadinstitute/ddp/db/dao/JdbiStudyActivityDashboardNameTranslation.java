package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiStudyActivityDashboardNameTranslation extends SqlObject {

    @SqlUpdate("insert into i18n_study_activity_dashboard_name_trans"
            + " (study_activity_id, language_code_id, translation_text) values (?,?,?)")
    @GetGeneratedKeys()
    long insert(long studyActivityId, long languageCodeId, String translatedText);

    @SqlUpdate("delete from i18n_study_activity_dashboard_name_trans where i18n_study_activity_dashboard_name_trans_id = :transId")
    int deleteById(long transId);
}
