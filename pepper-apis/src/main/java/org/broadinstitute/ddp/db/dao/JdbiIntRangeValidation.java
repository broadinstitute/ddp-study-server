package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.model.activity.instance.validation.IntRangeRule;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiIntRangeValidation extends SqlObject {

    @SqlUpdate("insert into int_range_validation (validation_id, min, max) values (:ruleId, :min, :max)")
    int insert(@Bind("ruleId") long validationId, @Bind("min") Long min, @Bind("max") Long max);

    default IntRangeRule findRuleByIdWithMessage(long id, String message, String correctionHint, boolean allowSave) {
        IntRangeRuleDto dto = _findRuleDtoWithId(id);
        return IntRangeRule.of(id, message, correctionHint, allowSave, dto.getMin(), dto.getMax());
    }

    @SqlQuery("select * from int_range_validation where validation_id = :id")
    @RegisterConstructorMapper(IntRangeRuleDto.class)
    IntRangeRuleDto _findRuleDtoWithId(@Bind("id") long id);

    class IntRangeRuleDto {
        private long ruleId;
        private Long max;
        private Long min;

        @JdbiConstructor
        public IntRangeRuleDto(@ColumnName("validation_id") Long ruleId, @ColumnName("min") Long min, @ColumnName("max") Long max) {
            this.ruleId = ruleId;
            this.min = min;
            this.max = max;
        }

        public long getRuleId() {
            return ruleId;
        }

        public Long getMax() {
            return max;
        }

        public Long getMin() {
            return min;
        }
    }
}
