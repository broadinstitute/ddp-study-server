package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.model.activity.definition.validation.AgeRangeRuleDef;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiAgeRangeValidation extends SqlObject {

    @SqlUpdate("insert into age_range_validation (validation_id, min_age, max_age)"
            + " values(:rule.ruleId, :rule.minAge, :rule.maxAge)")
    int insert(@BindBean("rule") AgeRangeRuleDef ruleDef);

}
