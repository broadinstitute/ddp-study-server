package org.broadinstitute.ddp.db.dto;

import lombok.Getter;
import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

import java.io.Serializable;
import java.util.Set;

@Getter
public final class EquationQuestionDto extends QuestionDto implements Serializable {
    private final Long placeholderTemplateId;
    private final Integer maximumDecimalPlaces;
    private final String expression;

    @JdbiConstructor
    public EquationQuestionDto(@Nested final QuestionDto questionDto,
                               @ColumnName("placeholder_template_id") final Long placeholderTemplateId,
                               @ColumnName("maximum_decimal_places") final Integer maximumDecimalPlaces,
                               @ColumnName("expression") final String expression) {
        super(questionDto);
        this.placeholderTemplateId = placeholderTemplateId;
        this.maximumDecimalPlaces = maximumDecimalPlaces;
        this.expression = expression;
    }

    @Override
    public Set<Long> getTemplateIds() {
        var ids = super.getTemplateIds();
        if (placeholderTemplateId != null) {
            ids.add(placeholderTemplateId);
        }
        return ids;
    }
}
