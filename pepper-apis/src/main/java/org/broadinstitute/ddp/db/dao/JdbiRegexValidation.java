package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiRegexValidation {

    @SqlUpdate("insert into regex_validation (validation_id, regex_pattern) values (:validationId, :pattern)")
    int insert(long validationId, String pattern);

}
