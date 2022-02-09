package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiNumericQuestion extends SqlObject {

    @SqlUpdate("insert into numeric_question (question_id, placeholder_template_id)"
            + " values (:questionId, :placeholderTemplateId)")
    int insert(@Bind("questionId") long questionId,
               @Bind("placeholderTemplateId") Long placeholderTemplateId);
}
