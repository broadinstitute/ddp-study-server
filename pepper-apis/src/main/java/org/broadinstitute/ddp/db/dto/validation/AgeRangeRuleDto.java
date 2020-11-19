package org.broadinstitute.ddp.db.dto.validation;

import java.io.Serializable;

import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public final class AgeRangeRuleDto extends RuleDto implements Serializable {

    private Integer minAge;
    private Integer maxAge;

    @JdbiConstructor
    public AgeRangeRuleDto(
            @Nested RuleDto ruleDto,
            @ColumnName("min") Integer minAge,
            @ColumnName("max") Integer maxAge) {
        super(ruleDto);
        this.minAge = minAge;
        this.maxAge = maxAge;
    }

    public Integer getMinAge() {
        return minAge;
    }

    public Integer getMaxAge() {
        return maxAge;
    }
}
