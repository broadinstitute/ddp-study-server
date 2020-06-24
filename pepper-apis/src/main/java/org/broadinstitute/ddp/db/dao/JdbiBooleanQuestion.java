package org.broadinstitute.ddp.db.dao;

import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.db.dto.BooleanQuestionDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface JdbiBooleanQuestion extends SqlObject {

    @SqlUpdate("insert into boolean_question (question_id,true_template_id,false_template_id)"
            + " values(:questionId,:trueTemplateId,:falseTemplateId)")
    int insert(@Bind("questionId")long questionId, @Bind("trueTemplateId")long trueTemplateId,
               @Bind("falseTemplateId") long falseTemplateId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryDtoByQuestionId")
    @RegisterConstructorMapper(BooleanQuestionDto.class)
    Optional<BooleanQuestionDto> findDtoByQuestionId(@Bind("questionId") long questionId);

    default Optional<BooleanQuestionDto> findDtoByQuestionId(QuestionDto questionDto) {
        return findDtoByQuestionId(questionDto.getId());
    }

    @UseStringTemplateSqlLocator
    @SqlQuery("queryDtoByActivityId")
    @RegisterConstructorMapper(BooleanQuestionDto.class)
    List<BooleanQuestionDto> findDtoByActivityId(@Bind("activityId") long activityId);
}
