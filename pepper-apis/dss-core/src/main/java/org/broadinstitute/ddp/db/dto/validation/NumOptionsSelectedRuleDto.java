package org.broadinstitute.ddp.db.dto.validation;

import java.io.Serializable;

import lombok.Value;
import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
public class NumOptionsSelectedRuleDto extends RuleDto implements Serializable {
    Integer minSelections;
    Integer maxSelections;

    @JdbiConstructor
    public NumOptionsSelectedRuleDto(
            @Nested RuleDto ruleDto,
            @ColumnName("min") Integer minSelections,
            @ColumnName("max") Integer maxSelections) {
        super(ruleDto);

        this.minSelections = minSelections;
        this.maxSelections = maxSelections;
    }
}
