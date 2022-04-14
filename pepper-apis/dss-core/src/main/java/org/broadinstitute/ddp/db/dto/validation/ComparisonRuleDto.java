package org.broadinstitute.ddp.db.dto.validation;

import lombok.Value;
import org.broadinstitute.ddp.model.activity.types.ComparisonType;
import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

import java.io.Serializable;

@Value
public class ComparisonRuleDto extends RuleDto implements Serializable {
    Long referenceQuestionId;
    ComparisonType type;

    @JdbiConstructor
    public ComparisonRuleDto(
            @Nested RuleDto ruleDto,
            @ColumnName("reference_question_id") Long referenceQuestionId,
            @ColumnName("comparison_validation_type") ComparisonType type) {
        super(ruleDto);

        this.referenceQuestionId = referenceQuestionId;
        this.type = type;
    }
}
