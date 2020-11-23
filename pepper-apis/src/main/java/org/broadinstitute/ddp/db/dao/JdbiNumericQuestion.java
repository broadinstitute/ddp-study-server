package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.model.activity.types.NumericType;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiNumericQuestion extends SqlObject {

    @SqlUpdate("insert into numeric_question (question_id, numeric_type_id, placeholder_template_id)"
            + " values (:questionId,"
            + "        (select numeric_type_id from numeric_type where numeric_type_code = :numericType),"
            + "        :placeholderTemplateId)")
    int insert(@Bind("questionId") long questionId,
               @Bind("numericType") NumericType numericType,
               @Bind("placeholderTemplateId") Long placeholderTemplateId);

}
