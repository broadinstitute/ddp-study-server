package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiLengthValidation extends SqlObject {

    @SqlUpdate("insert into length_validation (validation_id, min_length, max_length)"
            + " values (:validationId, :min, :max)")
    int insert(long validationId, Integer min, Integer max);

}
