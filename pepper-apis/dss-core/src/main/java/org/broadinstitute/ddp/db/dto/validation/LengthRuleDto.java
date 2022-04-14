package org.broadinstitute.ddp.db.dto.validation;

import java.io.Serializable;

import lombok.Value;
import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
public class LengthRuleDto extends RuleDto implements Serializable {
    Integer minLength;
    Integer maxLength;

    @JdbiConstructor
    public LengthRuleDto(
            @Nested RuleDto ruleDto,
            @ColumnName("min") Integer minLength,
            @ColumnName("max") Integer maxLength) {
        super(ruleDto);
        
        this.minLength = minLength;
        this.maxLength = maxLength;
    }
}

