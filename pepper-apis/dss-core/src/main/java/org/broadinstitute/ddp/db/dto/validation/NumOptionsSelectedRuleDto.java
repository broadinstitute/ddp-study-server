package org.broadinstitute.ddp.db.dto.validation;

import java.io.Serializable;

import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public final class NumOptionsSelectedRuleDto extends RuleDto implements Serializable {

    private Integer minSelections;
    private Integer maxSelections;

    @JdbiConstructor
    public NumOptionsSelectedRuleDto(
            @Nested RuleDto ruleDto,
            @ColumnName("min") Integer minSelections,
            @ColumnName("max") Integer maxSelections) {
        super(ruleDto);
        this.minSelections = minSelections;
        this.maxSelections = maxSelections;
    }

    public Integer getMinSelections() {
        return minSelections;
    }

    public Integer getMaxSelections() {
        return maxSelections;
    }
}
