package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiActivityInstanceSelectQuestion extends SqlObject {

    @SqlUpdate("insert into activity_instance_select_question (activity_instance_select_question_id) values (:questionId)")
    int insert(@Bind("questionId") long questionId);
}
