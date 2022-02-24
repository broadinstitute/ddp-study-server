package org.broadinstitute.ddp.db.dto;

import java.io.Serializable;
import java.util.Set;

import lombok.Value;
import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
public class BooleanQuestionDto extends QuestionDto implements Serializable {
    long trueTemplateId;
    long falseTemplateId;

    @JdbiConstructor
    public BooleanQuestionDto(@Nested QuestionDto questionDto,
                              @ColumnName("true_template_id") long trueTemplateId,
                              @ColumnName("false_template_id") long falseTemplateId) {
        super(questionDto);
        this.trueTemplateId = trueTemplateId;
        this.falseTemplateId = falseTemplateId;
    }

    @Override
    public Set<Long> getTemplateIds() {
        var ids = super.getTemplateIds();
        ids.add(trueTemplateId);
        ids.add(falseTemplateId);
        return ids;
    }
}
