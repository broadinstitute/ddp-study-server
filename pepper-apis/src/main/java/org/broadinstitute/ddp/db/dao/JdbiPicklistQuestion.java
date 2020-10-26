package org.broadinstitute.ddp.db.dao;

import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.db.dto.PicklistQuestionDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.model.activity.types.PicklistRenderMode;
import org.broadinstitute.ddp.model.activity.types.PicklistSelectMode;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface JdbiPicklistQuestion extends SqlObject {

    @UseStringTemplateSqlLocator
    @SqlUpdate("insertByModeIds")
    int insert(@Bind("questionId") long questionId,
               @Bind("selectModeId") long selectModeId,
               @Bind("renderModeId") long renderModeId,
               @Bind("picklistLabelTemplateId") Long picklistLabelTemplateId);

    @UseStringTemplateSqlLocator
    @SqlUpdate("insertByModeCodes")
    int insert(@Bind("questionId") long questionId,
               @Bind("selectMode") PicklistSelectMode selectMode,
               @Bind("renderMode") PicklistRenderMode renderMode,
               @Bind("picklistLabelTemplateId") Long picklistLabelTemplateId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryDtoByQuestionId")
    @RegisterConstructorMapper(PicklistQuestionDto.class)
    Optional<PicklistQuestionDto> findDtoByQuestionId(@Bind("questionId") long questionId);

    default Optional<PicklistQuestionDto> findDtoByQuestion(QuestionDto questionDto) {
        return findDtoByQuestionId(questionDto.getId());
    }

    @UseStringTemplateSqlLocator
    @SqlQuery("queryDtoByActivityId")
    @RegisterConstructorMapper(PicklistQuestionDto.class)
    List<PicklistQuestionDto> findDtosByActivityId(@Bind("activityId") long activityId);
}
