package org.broadinstitute.ddp.db.dto;

import org.broadinstitute.ddp.model.activity.types.MatrixSelectMode;
import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

import java.io.Serializable;
import java.util.Set;

/**
 * DTO class to represent matrix question that includes all base question data.
 */
public final class MatrixQuestionDto extends QuestionDto implements Serializable {

    private MatrixSelectMode selectMode;
    private boolean renderModal;
    private Long modalTemplateId;
    private Long modalTitleTemplateId;

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

    public MatrixSelectMode getSelectMode() {
        return selectMode;
    }

    public boolean isRenderModal() {
        return renderModal;
    }

    public Long getModalTemplateId() {
        return modalTemplateId;
    }

    public Long getModalTitleTemplateId() {
        return modalTitleTemplateId;
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
