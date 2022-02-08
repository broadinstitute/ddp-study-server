package org.broadinstitute.ddp.db.dto.validation;

import java.io.Serializable;

import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public final class IntRangeRuleDto extends RuleDto implements Serializable {

    private Long min;
    private Long max;

    @JdbiConstructor
    public IntRangeRuleDto(
            @Nested RuleDto ruleDto,
            @ColumnName("min") Long min,
            @ColumnName("max") Long max) {
        super(ruleDto);
        this.min = min;
        this.max = max;
    }

    public Long getMin() {
        return min;
    }

    public Long getMax() {
        return max;
    }
}
