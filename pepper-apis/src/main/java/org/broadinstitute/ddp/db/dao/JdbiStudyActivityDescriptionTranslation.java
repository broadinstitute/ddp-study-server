package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiStudyActivityDescriptionTranslation extends SqlObject {

    @SqlUpdate("insert into i18n_study_activity_description_trans"
            + " (study_activity_id, language_code_id, translation_text)"
            + "values (:studyActivityId, :languageCodeId, :translatedText)"
    )
    @GetGeneratedKeys
    long insert(
            @Bind("studyActivityId") long studyActivityId,
            @Bind("languageCodeId") long languageCodeId,
            @Bind("translatedText") String translatedText
    );

    @SqlUpdate("delete from i18n_study_activity_description_trans where i18n_study_activity_description_trans_id = :transId")
    int deleteById(long transId);
}
