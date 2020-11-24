package org.broadinstitute.ddp.db.dto.validation;

import java.io.Serializable;

import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public final class LengthRuleDto extends RuleDto implements Serializable {

    private Integer minLength;
    private Integer maxLength;

    @JdbiConstructor
    public LengthRuleDto(
            @Nested RuleDto ruleDto,
            @ColumnName("min") Integer minLength,
            @ColumnName("max") Integer maxLength) {
        super(ruleDto);
        this.minLength = minLength;
        this.maxLength = maxLength;
    }

    public Integer getMinLength() {
        return minLength;
    }

    public Integer getMaxLength() {
        return maxLength;
    }
}

