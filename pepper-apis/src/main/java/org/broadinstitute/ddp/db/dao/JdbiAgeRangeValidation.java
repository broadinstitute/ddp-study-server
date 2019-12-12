package org.broadinstitute.ddp.db.dao;

import java.beans.ConstructorProperties;

import org.broadinstitute.ddp.model.activity.definition.validation.AgeRangeRuleDef;
import org.broadinstitute.ddp.model.activity.instance.validation.AgeRangeRule;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiAgeRangeValidation extends SqlObject {
    @SqlUpdate("insert into age_range_validation (validation_id, min_age, max_age)"
            + " values(:rule.ruleId, :rule.minAge, :rule.maxAge)")
    int insert(@BindBean("rule")AgeRangeRuleDef ruleDef);

    default AgeRangeRule findRuleByIdWithMessage(long id, String message, String correctionHint, boolean allowSave) {
        AgeRangeRuleData data = _findRuleDataWithId(id);
        return AgeRangeRule.of(id, message, correctionHint, allowSave, data.minAge, data.maxAge);
    }

    /**
     * Meant to be private
     * @param id validation db id
     * @return AgeRange basic data
     */
    @SqlQuery("select * from age_range_validation where validation_id = :id")
    @RegisterConstructorMapper(AgeRangeRuleData.class)
    AgeRangeRuleData _findRuleDataWithId(long id);

    class AgeRangeRuleData {
        Long ruleId;
        Integer maxAge;
        Integer minAge;

        @ConstructorProperties({"validation_id", "max_age", "min_age"})
        public AgeRangeRuleData(Long ruleId, Integer maxAge, Integer minAge) {
            this.ruleId = ruleId;
            this.maxAge = maxAge;
            this.minAge = minAge;
        }
    }
}
