package org.broadinstitute.ddp.db.dto;

import java.io.Serializable;
import java.util.Set;

import lombok.Value;
import org.broadinstitute.ddp.model.activity.types.PicklistRenderMode;
import org.broadinstitute.ddp.model.activity.types.PicklistSelectMode;
import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
public class PicklistQuestionDto extends QuestionDto implements Serializable {
    PicklistSelectMode selectMode;
    PicklistRenderMode renderMode;
    Long labelTemplateId;

    @JdbiConstructor
    public PicklistQuestionDto(@Nested QuestionDto questionDto,
                               @ColumnName("picklist_select_mode") PicklistSelectMode selectMode,
                               @ColumnName("picklist_render_mode") PicklistRenderMode renderMode,
                               @ColumnName("picklist_label_template_id") Long labelTemplateId) {
        super(questionDto);
        this.selectMode = selectMode;
        this.renderMode = renderMode;
        this.labelTemplateId = labelTemplateId;
    }

    @Override
    public Set<Long> getTemplateIds() {
        var ids = super.getTemplateIds();
        if (labelTemplateId != null) {
            ids.add(labelTemplateId);
        }
        return ids;
    }
}
