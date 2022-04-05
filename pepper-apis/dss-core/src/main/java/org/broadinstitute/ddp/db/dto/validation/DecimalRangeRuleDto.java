package org.broadinstitute.ddp.db.dto.validation;

import lombok.Value;
import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Value
public class DecimalRangeRuleDto extends RuleDto implements Serializable {
    BigDecimal min;
    BigDecimal max;

    @JdbiConstructor
    public DecimalRangeRuleDto(
            @Nested RuleDto ruleDto,
            @ColumnName("min") BigDecimal min,
            @ColumnName("max") BigDecimal max) {
        super(ruleDto);

        this.min = min;
        this.max = max;
    }
}
