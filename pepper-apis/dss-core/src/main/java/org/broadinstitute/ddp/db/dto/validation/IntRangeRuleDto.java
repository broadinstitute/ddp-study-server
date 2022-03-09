package org.broadinstitute.ddp.db.dto.validation;

import java.io.Serializable;

import lombok.Value;
import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
public class IntRangeRuleDto extends RuleDto implements Serializable {
    Long min;
    Long max;

    @JdbiConstructor
    public IntRangeRuleDto(
            @Nested RuleDto ruleDto,
            @ColumnName("min") Long min,
            @ColumnName("max") Long max) {
        super(ruleDto);

        this.min = min;
        this.max = max;
    }
}
