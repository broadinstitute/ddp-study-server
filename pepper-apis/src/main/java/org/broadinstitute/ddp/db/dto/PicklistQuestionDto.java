package org.broadinstitute.ddp.db.dto;

import java.io.Serializable;

import org.broadinstitute.ddp.model.activity.types.PicklistRenderMode;
import org.broadinstitute.ddp.model.activity.types.PicklistSelectMode;
import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

/**
 * DTO class to represent picklist question that includes all base question data.
 */
public final class PicklistQuestionDto extends QuestionDto implements Serializable {

    private PicklistSelectMode selectMode;
    private PicklistRenderMode renderMode;
    private Long labelTemplateId;

    @JdbiConstructor
    public PicklistQuestionDto(@Nested QuestionDto questionDto,
                               @ColumnName("picklist_select_mode_code") PicklistSelectMode selectMode,
                               @ColumnName("picklist_render_mode_code") PicklistRenderMode renderMode,
                               @ColumnName("picklist_label_template_id") Long labelTemplateId) {
        super(questionDto);
        this.selectMode = selectMode;
        this.renderMode = renderMode;
        this.labelTemplateId = labelTemplateId;
    }

    public PicklistSelectMode getSelectMode() {
        return selectMode;
    }

    public PicklistRenderMode getRenderMode() {
        return renderMode;
    }

    public Long getLabelTemplateId() {
        return labelTemplateId;
    }
}
