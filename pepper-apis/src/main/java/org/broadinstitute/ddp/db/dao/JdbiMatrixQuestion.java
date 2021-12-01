package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.model.activity.types.MatrixSelectMode;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiMatrixQuestion extends SqlObject {

    @SqlUpdate("insert into matrix_question(question_id, matrix_select_mode_id) values (:questionId, :selectModeId)")
    int insert(@Bind("questionId") long questionId,
               @Bind("selectModeId") long selectModeId);

    @SqlUpdate("insert into matrix_question(question_id, matrix_select_mode_id) values (:questionId, "
            + "(select matrix_select_mode_id from matrix_select_mode where matrix_select_mode_code = :selectMode))")
    int insert(@Bind("questionId") long questionId,
               @Bind("selectMode") MatrixSelectMode selectMode);

}
