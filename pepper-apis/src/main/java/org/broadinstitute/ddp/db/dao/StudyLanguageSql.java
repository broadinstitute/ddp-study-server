package org.broadinstitute.ddp.db.dao;

import java.util.List;

import org.broadinstitute.ddp.model.study.StudyLanguage;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
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

    @SqlUpdate("delete from study_language where study_language_id = :id")
    int deleteById(@Bind("id") long id);

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

    @SqlQuery("select lc.iso_language_code, "
            + "sl.name, "
            + "sl.is_default, "
            + "sl.umbrella_study_id, "
            + "sl.language_code_id "
            + "from study_language as sl join language_code as lc "
            + "on sl.language_code_id = lc.language_code_id "
            + "where sl.umbrella_study_id = :umbrellaStudyId "
            + "order by sl.is_default desc")
    @RegisterConstructorMapper(StudyLanguage.class)
    List<StudyLanguage> selectStudyLanguages(@Bind("umbrellaStudyId") long umbrellaStudyId);

}
