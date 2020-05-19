package org.broadinstitute.ddp.db.dao;

import java.util.List;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface StudyLanguageSql extends SqlObject {

    @SqlUpdate("insert into study_language "
            + "(umbrella_study_id, language_code_id, name) values "
            + "(:studyId, :languageCodeId, :name)")
    @GetGeneratedKeys()
    long insert(@Bind("studyId") long studyId,
                @Bind("languageCodeId") long languageCodeId,
                @Bind("name") String name);

    @SqlUpdate("insert into study_language "
            + "(umbrella_study_id, language_code_id) values "
            + "(:studyId, :languageCodeId)")
    @GetGeneratedKeys()
    long insert(@Bind("studyId") long studyId,
                @Bind("languageCodeId") long languageCodeId);

    @SqlUpdate("update study_language "
            + " set is_default = :isDefault  "
            + " where umbrella_study_id = :studyId and language_code_id = :languageCodeId")
    int updateDefaultLanguage(@Bind("studyId") long studyId,
                @Bind("languageCodeId") long languageCodeId,
                @Bind("isDefault") boolean isDefault);

    @SqlUpdate("update study_language "
            + " set is_default = false  "
            + " where umbrella_study_id = :studyId")
    int updateExistingAsNonDefaultLanguages(@Bind("studyId") long studyId);

    @SqlQuery("select language_code_id from study_language "
            + " where umbrella_study_id = :studyId and is_default = true")
    List<Long> selectDefaultLanguageCodeId(long studyId);

}
