package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.db.dto.EquationQuestionDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.List;

public interface JdbiEquationQuestion extends SqlObject {
    @SqlUpdate("insert into equation_question (question_id, placeholder_template_id, maximum_decimal_places, expression)"
            + " values (:questionId, :placeholderTemplateId, :maximumDecimalPlaces, :expression)")
    int insert(@Bind("questionId") final long questionId,
               @Bind("placeholderTemplateId") final Long placeholderTemplateId,
               @Bind("maximumDecimalPlaces") final Integer maximumDecimalPlaces,
               @Bind("expression") final String expression);


    @SqlQuery("select q.*, eq.expression, eq.maximum_decimal_places, qt.question_type_code as question_type "
            + "from equation_question eq "
            + "join question q "
            + "  on q.question_id = eq.question_id "
            + "join question_type qt "
            + "  on qt.question_type_id = q.question_type_id "
            + "join activity_instance ai "
            + "  on ai.study_activity_id = q.study_activity_id "
            + "where ai.activity_instance_guid = :activityInstanceGuid")
    @RegisterConstructorMapper(EquationQuestionDto.class)
    List<EquationQuestionDto> findEquationsByActivityInstanceGuid(@Bind("activityInstanceGuid") String activityInstanceGuid);
}
