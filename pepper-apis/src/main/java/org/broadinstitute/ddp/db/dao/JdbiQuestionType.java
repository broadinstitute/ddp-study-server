package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

public interface JdbiQuestionType extends SqlObject {

    @SqlQuery("select question_type_id from question_type where question_type_code = ?")
    long getTypeId(QuestionType questionType);
}
