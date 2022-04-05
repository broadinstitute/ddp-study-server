package org.broadinstitute.ddp.db.dto;

import lombok.Value;
import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

import java.io.Serializable;
import java.util.Set;

@Value
public class NumericQuestionDto extends QuestionDto implements Serializable {
    Long placeholderTemplateId;

    @JdbiConstructor
    public NumericQuestionDto(@Nested QuestionDto questionDto,
                              @ColumnName("placeholder_template_id") Long placeholderTemplateId) {
        super(questionDto);
        this.placeholderTemplateId = placeholderTemplateId;
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
