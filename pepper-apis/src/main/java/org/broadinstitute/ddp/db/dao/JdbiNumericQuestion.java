package org.broadinstitute.ddp.db.dao;

import java.util.Optional;

import org.broadinstitute.ddp.db.dto.NumericQuestionDto;
import org.broadinstitute.ddp.model.activity.types.NumericType;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiNumericQuestion extends SqlObject {

    @SqlUpdate("insert into numeric_question (question_id, numeric_type_id, placeholder_template_id)"
            + " values (:questionId,"
            + "        (select numeric_type_id from numeric_type where numeric_type_code = :numericType),"
            + "        :placeholderTemplateId)")
    int insert(@Bind("questionId") long questionId,
               @Bind("numericType") NumericType numericType,
               @Bind("placeholderTemplateId") Long placeholderTemplateId);

    @SqlQuery("select nt.numeric_type_code as numeric_type,"
            + "       nq.placeholder_template_id,"
            + "       qt.question_type_code,"
            + "       qsc.stable_id,"
            + "       q.*,"
            + "       rev.start_date as revision_start,"
            + "       rev.end_date as revision_end"
            + "  from numeric_question as nq"
            + "  join numeric_type as nt on nq.numeric_type_id = nt.numeric_type_id"
            + "  join question as q on q.question_id = nq.question_id"
            + "  join question_type as qt on qt.question_type_id = q.question_type_id"
            + "  join question_stable_code as qsc on qsc.question_stable_code_id = q.question_stable_code_id"
            + "  join revision as rev on rev.revision_id = q.revision_id"
            + " where nq.question_id = :questionId")
    @RegisterConstructorMapper(NumericQuestionDto.class)
    Optional<NumericQuestionDto> findDtoByQuestionId(@Bind("questionId") long questionId);
}
