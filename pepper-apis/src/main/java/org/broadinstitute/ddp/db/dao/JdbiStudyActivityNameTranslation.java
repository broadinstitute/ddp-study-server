package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiStudyActivityNameTranslation extends SqlObject {

    @SqlUpdate("insert into i18n_study_activity_name_trans"
            + " (study_activity_id,language_code_id,translation_text) values (?,?,?)")
    @GetGeneratedKeys()
    long insert(long studyActivityId, long languageCodeId, String translatedText);

    @SqlQuery("select t.translation_text \n"
            + "from umbrella_study s join study_activity as a on a.study_id =  s.umbrella_study_id\n"
            + "join i18n_study_activity_name_trans as t on t.study_activity_id = a.study_activity_id\n"
            + "join language_code as l on l.language_code_id = t.language_code_id\n"
            + "and s.guid = :studyGuid and a.study_activity_code = :activityCode and l.iso_language_code = :languageCode ")
    String getActivityNameByStudyAndActivityCode(@Bind("studyGuid") String studyGuid,
                                                 @Bind("activityCode") String activityCode,
                                                 @Bind("languageCode") String languageCode);

}
