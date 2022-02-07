package org.broadinstitute.ddp.db.dto;

import java.io.Serializable;
import java.util.Set;

import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public final class NumericQuestionDto extends QuestionDto implements Serializable {
    private final Long placeholderTemplateId;

    @JdbiConstructor
    public NumericQuestionDto(@Nested QuestionDto questionDto,
                              @ColumnName("placeholder_template_id") Long placeholderTemplateId) {
        super(questionDto);
        this.placeholderTemplateId = placeholderTemplateId;
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
