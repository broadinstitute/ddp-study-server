package org.broadinstitute.ddp.db.dao;

import java.math.BigDecimal;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiDecimalRangeValidation extends SqlObject {

    @SqlUpdate("insert into decimal_range_validation (validation_id, min, max) values (:ruleId, :min, :max)")
    int insert(@Bind("ruleId") long validationId, @Bind("min") BigDecimal min, @Bind("max") BigDecimal max);

}
