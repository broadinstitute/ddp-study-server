package org.broadinstitute.ddp.db.dto.validation;

import java.io.Serializable;

import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public final class RegexRuleDto extends RuleDto implements Serializable {

    private String regexPattern;

    @JdbiConstructor
    public RegexRuleDto(
            @Nested RuleDto ruleDto,
            @ColumnName("regex_pattern") String regexPattern) {
        super(ruleDto);
        this.regexPattern = regexPattern;
    }

    public String getRegexPattern() {
        return regexPattern;
    }
}
