package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.model.activity.types.MatrixSelectMode;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiMatrixQuestion extends SqlObject {

    @SqlUpdate("insert into matrix_question(question_id, matrix_select_mode_id, render_modal, modal_template_id,"
            + " modal_title_template_id) "
            + "values (:questionId, :selectModeId, :renderModal, :modalTemplateId, :modalTitleTemplateId)")
    int insert(@Bind("questionId") long questionId,
               @Bind("selectModeId") long selectModeId,
               @Bind("renderModal") boolean renderModal,
               @Bind("modalTemplateId") Long modalTemplateId,
               @Bind("modalTitleTemplateId") Long modalTitleTemplateId);

    @SqlUpdate("insert into matrix_question(question_id, matrix_select_mode_id, render_modal, modal_template_id, "
            + "modal_title_template_id) "
            + "values (:questionId, "
            + "(select matrix_select_mode_id from matrix_select_mode where matrix_select_mode_code = :selectMode),"
            + " :renderModal, :modalTemplateId, :modalTitleTemplateId)")
    int insert(@Bind("questionId") long questionId,
               @Bind("selectMode") MatrixSelectMode selectMode,
               @Bind("renderModal") boolean renderModal,
               @Bind("modalTemplateId") Long modalTemplateId,
               @Bind("modalTitleTemplateId") Long modalTitleTemplateId);

}
