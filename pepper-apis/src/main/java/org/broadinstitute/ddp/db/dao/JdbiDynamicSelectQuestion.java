package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiDynamicSelectQuestion extends SqlObject {

    @SqlUpdate("insert into dynamic_select_question (dynamic_question_id) values (:questionId)")
    int insert(@Bind("questionId") long questionId);
}
