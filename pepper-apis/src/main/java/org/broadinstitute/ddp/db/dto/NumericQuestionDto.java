package org.broadinstitute.ddp.db.dto;

import java.util.Set;

import org.broadinstitute.ddp.model.activity.types.NumericType;
import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public final class NumericQuestionDto extends QuestionDto {

    private NumericType numericType;
    private Long placeholderTemplateId;

    @JdbiConstructor
    public NumericQuestionDto(@Nested QuestionDto questionDto,
                              @ColumnName("numeric_type") NumericType numericType,
                              @ColumnName("placeholder_template_id") Long placeholderTemplateId) {
        super(questionDto);
        this.numericType = numericType;
        this.placeholderTemplateId = placeholderTemplateId;
    }

    public NumericType getNumericType() {
        return numericType;
    }

    public Long getPlaceholderTemplateId() {
        return placeholderTemplateId;
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
