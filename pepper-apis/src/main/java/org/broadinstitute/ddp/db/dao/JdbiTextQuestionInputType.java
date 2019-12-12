package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

public interface JdbiTextQuestionInputType extends SqlObject {

    @SqlQuery("select tqit.text_question_input_type_code"
            + " from text_question as tq"
            + " join text_question_input_type as tqit on tqit.text_question_input_type_id = tq.input_type_id"
            + " where tq.question_id = :id"
    )
    TextInputType getTextQuestionInputTypeByQuestionId(@Bind("id") long id);
}
