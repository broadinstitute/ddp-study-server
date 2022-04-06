package org.broadinstitute.ddp.db.dto.validation;

import java.io.Serializable;

import lombok.Value;
import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
public class AgeRangeRuleDto extends RuleDto implements Serializable {
    Integer minAge;
    Integer maxAge;

    @JdbiConstructor
    public AgeRangeRuleDto(@Nested RuleDto ruleDto, @ColumnName("min") Integer minAge, @ColumnName("max") Integer maxAge) {
        super(ruleDto);

        this.minAge = minAge;
        this.maxAge = maxAge;
    }
}
