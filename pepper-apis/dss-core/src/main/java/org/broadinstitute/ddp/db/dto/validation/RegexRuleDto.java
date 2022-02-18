package org.broadinstitute.ddp.db.dto.validation;

import java.io.Serializable;

import lombok.Value;
import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
public class RegexRuleDto extends RuleDto implements Serializable {
    String regexPattern;

    @JdbiConstructor
    public RegexRuleDto(
            @Nested RuleDto ruleDto,
            @ColumnName("regex_pattern") String regexPattern) {
        super(ruleDto);

        this.regexPattern = regexPattern;
    }
}
