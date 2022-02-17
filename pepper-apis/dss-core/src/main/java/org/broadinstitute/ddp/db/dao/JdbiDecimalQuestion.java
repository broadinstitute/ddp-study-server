package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiDecimalQuestion extends SqlObject {

    @SqlUpdate("insert into decimal_question (question_id, placeholder_template_id, scale)"
            + " values (:questionId, :placeholderTemplateId, :scale)")
    int insert(@Bind("questionId") long questionId,
               @Bind("placeholderTemplateId") Long placeholderTemplateId,
               @Bind("scale") Integer scale);
}
