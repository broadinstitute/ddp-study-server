package org.broadinstitute.ddp.db.dao;

import java.util.Optional;

import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface JdbiQuestion extends SqlObject {

    @SqlUpdate("insert into question (question_type_id, is_restricted, question_stable_code_id,"
            + " question_prompt_template_id, info_header_template_id, info_footer_template_id,"
            + " revision_id, study_activity_id, hide_number, is_deprecated)"
            + " values(:questionTypeId, :isRestricted, :stableCodeId, :promptTemplateId,"
            + " :infoHeaderTemplateId, :infoFooterTemplateId, :revisionId, :activityId,"
            + " :hideNumber, :isDeprecated)")
    @GetGeneratedKeys
    long insert(@Bind("questionTypeId") long questionTypeId, @Bind("isRestricted") boolean isRestricted,
                @Bind("stableCodeId") long stableCodeId, @Bind("promptTemplateId") long promptTemplateId,
                @Bind("infoHeaderTemplateId") Long infoHeaderTemplateId, @Bind("infoFooterTemplateId") Long infoFooterTemplateId,
                @Bind("revisionId") long revisionId, @Bind("activityId") long activityId,
                @Bind("hideNumber") boolean hideNumber, @Bind("isDeprecated") boolean isDeprecated);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryQuestionDtoByStableIdAndInstanceGuid")
    @RegisterConstructorMapper(QuestionDto.class)
    Optional<QuestionDto> findDtoByStableIdAndInstanceGuid(@Bind("stableId") String stableId,
                                                           @Bind("instanceGuid") String instanceGuid);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryQuestionDtoByStudyIdStableIdAndUserGuid")
    @RegisterConstructorMapper(QuestionDto.class)
    Optional<QuestionDto> findDtoByStudyIdStableIdAndUserGuid(@Bind("studyId") long studyId,
                                                              @Bind("stableId") String stableId,
                                                              @Bind("userGuid") String userGuid);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryQuestionInfoIfActiveByQuestionId")
    @RegisterConstructorMapper(QuestionDto.class)
    Optional<QuestionDto> getQuestionDtoIfActive(@Bind("questionId") long questionId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryQuestionInfoByQuestionId")
    @RegisterConstructorMapper(QuestionDto.class)
    Optional<QuestionDto> getQuestionDtoById(@Bind("questionId") long questionId);

    // study-builder
    @UseStringTemplateSqlLocator
    @SqlQuery("queryLatestQuestionDtoByQuestionStableIdAndUmbrellaStudyId")
    @RegisterConstructorMapper(QuestionDto.class)
    Optional<QuestionDto> getLatestQuestionDtoByQuestionStableIdAndUmbrellaStudyId(@Bind("questionStableId") String questionStableId,
                                                                                   @Bind ("umbrellaStudyId") long umbrellaStudyId);

    @SqlUpdate("update question set revision_id = :revisionId where question_id = :questionId")
    int updateRevisionIdById(@Bind("questionId") long questionId, @Bind("revisionId") long revisionId);

    @SqlUpdate("update question set is_deprecated = :isDeprecated where question_id = :questionId")
    int updateIsDeprecatedById(@Bind("questionId") long questionId, @Bind("isDeprecated") boolean isDeprecated);
}
