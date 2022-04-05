package org.broadinstitute.ddp.db.dto;

import lombok.Getter;
import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

import java.io.Serializable;
import java.util.Set;

@Getter
public final class DecimalQuestionDto extends QuestionDto implements Serializable {
    private final Long placeholderTemplateId;
    private final Integer scale;

    @JdbiConstructor
    public DecimalQuestionDto(@Nested QuestionDto questionDto,
                              @ColumnName("placeholder_template_id") Long placeholderTemplateId,
                              @ColumnName("scale") Integer scale) {
        super(questionDto);
        this.placeholderTemplateId = placeholderTemplateId;
        this.scale = scale;
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
