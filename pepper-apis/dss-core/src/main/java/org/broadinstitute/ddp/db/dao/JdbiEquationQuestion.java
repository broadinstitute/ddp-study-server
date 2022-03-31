package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiEquationQuestion extends SqlObject {
    @SqlUpdate("insert into equation_question (question_id, placeholder_template_id, maximum_decimal_places, expression)"
            + " values (:questionId, :placeholderTemplateId, :maximumDecimalPlaces, :expression)")
    int insert(@Bind("questionId") final long questionId,
               @Bind("placeholderTemplateId") final Long placeholderTemplateId,
               @Bind("maximumDecimalPlaces") final Integer maximumDecimalPlaces,
               @Bind("expression") final String expression);
}
