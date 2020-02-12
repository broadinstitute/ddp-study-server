package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiStudyLanguage {
    @SqlUpdate(
            "insert into study_language(umbrella_study_id, language_code_id) values ("
            + " (select umbrella_study_id from umbrella_study where guid = :studyGuid),"
            + " (select language_code_id from language_code where iso_language_code = :isoCode))"
    )
    int insert(@Bind("studyGuid") String umbrellaStudyGuid, @Bind("isoCode") String isoLanguageCode);
}
