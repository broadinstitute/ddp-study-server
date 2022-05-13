package org.broadinstitute.ddp.db.dto;

import java.util.Set;

import lombok.Value;
import org.broadinstitute.ddp.model.activity.types.BooleanRenderMode;
import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
public class BooleanQuestionDto extends QuestionDto {
    BooleanRenderMode renderMode;
    long trueTemplateId;
    long falseTemplateId;

    @JdbiConstructor
    public BooleanQuestionDto(@Nested QuestionDto questionDto,
                              @ColumnName("true_template_id") long trueTemplateId,
                              @ColumnName("false_template_id") long falseTemplateId,
                              @ColumnName("boolean_render_mode") BooleanRenderMode renderMode) {
        super(questionDto);
        this.trueTemplateId = trueTemplateId;
        this.falseTemplateId = falseTemplateId;
        this.renderMode = renderMode;
    }

    @Override
    public Set<Long> getTemplateIds() {
        var ids = super.getTemplateIds();
        ids.add(trueTemplateId);
        ids.add(falseTemplateId);
        return ids;
    }
}
