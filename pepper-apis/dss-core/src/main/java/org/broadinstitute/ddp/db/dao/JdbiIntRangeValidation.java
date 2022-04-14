package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiIntRangeValidation extends SqlObject {

    @SqlUpdate("insert into int_range_validation (validation_id, min, max) values (:ruleId, :min, :max)")
    int insert(@Bind("ruleId") long validationId, @Bind("min") Long min, @Bind("max") Long max);

}
