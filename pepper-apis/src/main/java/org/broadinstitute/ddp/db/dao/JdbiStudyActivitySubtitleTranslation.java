package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiStudyActivitySubtitleTranslation extends SqlObject {

    @SqlUpdate("insert into i18n_study_activity_subtitle_trans"
            + " (study_activity_id,language_code_id,translation_text) values (?,?,?)")
    @GetGeneratedKeys()
    long insert(long studyActivityId, long languageCodeId, String translatedText);
}
