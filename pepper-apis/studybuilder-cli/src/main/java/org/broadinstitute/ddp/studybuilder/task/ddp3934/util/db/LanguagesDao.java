package org.broadinstitute.ddp.studybuilder.task.ddp3934.util.db;

import java.util.List;

import org.broadinstitute.ddp.studybuilder.task.ddp3934.util.model.Language;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

public interface LanguagesDao {
    @SqlQuery("SELECT lc.language_code_id, lc.iso_language_code "
                + "FROM language_code AS lc "
                + "ORDER BY language_code_id")
    @RegisterConstructorMapper(Language.class)
    List<Language> getLanguages();
}
