package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface StudyLanguageSql extends SqlObject {

    @SqlUpdate("insert into study_language "
            + "(umbrella_study_id, language_code_id, is_default) values "
            + "(:studyId, :languageCodeId, :isDefault)")
    @GetGeneratedKeys()
    long insert(@Bind("studyId") long umbrellaStudyId,
                @Bind("languageCodeId") long languageCodeId,
                @Bind("isDefault") Boolean isDefault);

}
