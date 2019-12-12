package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

public interface JdbiLanguageCode extends SqlObject {

    @SqlQuery("select language_code_id from language_code where iso_language_code = ?")
    Long getLanguageCodeId(String languageCode);
}
