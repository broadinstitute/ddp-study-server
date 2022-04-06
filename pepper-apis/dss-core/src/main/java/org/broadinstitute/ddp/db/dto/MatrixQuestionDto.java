package org.broadinstitute.ddp.db.dto;

import lombok.Value;
import org.broadinstitute.ddp.model.activity.types.MatrixSelectMode;
import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

import java.io.Serializable;
import java.util.Set;

@Value
public class MatrixQuestionDto extends QuestionDto implements Serializable {
    MatrixSelectMode selectMode;
    boolean renderModal;
    Long modalTemplateId;
    Long modalTitleTemplateId;

    @JdbiConstructor
    public MatrixQuestionDto(@Nested QuestionDto questionDto,
                             @ColumnName("matrix_select_mode") MatrixSelectMode selectMode,
                             @ColumnName("render_modal") boolean renderModal,
                             @ColumnName("modal_template_id") Long modalTemplateId,
                             @ColumnName("modal_title_template_id") Long modalTitleTemplateId
                             ) {
        super(questionDto);
        this.selectMode = selectMode;
        this.renderModal = renderModal;
        this.modalTemplateId = modalTemplateId;
        this.modalTitleTemplateId = modalTitleTemplateId;
    }

    @Override
    public Set<Long> getTemplateIds() {
        var ids = super.getTemplateIds();
        if (modalTemplateId != null) {
            ids.add(modalTemplateId);
        }
        if (modalTitleTemplateId != null) {
            ids.add(modalTitleTemplateId);
        }
        return ids;
    }
}
