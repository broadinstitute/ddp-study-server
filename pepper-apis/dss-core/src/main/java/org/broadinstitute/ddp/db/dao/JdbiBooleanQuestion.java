package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiBooleanQuestion extends SqlObject {

    @SqlUpdate("insert into boolean_question (question_id,true_template_id,false_template_id)"
            + " values(:questionId,:trueTemplateId,:falseTemplateId)")
    int insert(@Bind("questionId") long questionId, @Bind("trueTemplateId") long trueTemplateId,
               @Bind("falseTemplateId") long falseTemplateId);

}
