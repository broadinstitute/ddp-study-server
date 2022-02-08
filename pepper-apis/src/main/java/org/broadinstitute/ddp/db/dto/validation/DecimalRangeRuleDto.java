package org.broadinstitute.ddp.db.dto.validation;

import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

public final class DecimalRangeRuleDto extends RuleDto implements Serializable {

    private BigDecimal min;
    private BigDecimal max;

    @JdbiConstructor
    public DecimalRangeRuleDto(
            @Nested RuleDto ruleDto,
            @ColumnName("min") BigDecimal min,
            @ColumnName("max") BigDecimal max) {
        super(ruleDto);
        this.min = min;
        this.max = max;
    }

    public BigDecimal getMin() {
        return min;
    }

    public BigDecimal getMax() {
        return max;
    }
}
