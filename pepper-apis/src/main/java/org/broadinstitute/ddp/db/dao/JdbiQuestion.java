package org.broadinstitute.ddp.db.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.broadinstitute.ddp.constants.SqlConstants;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.AgreementQuestionDto;
import org.broadinstitute.ddp.db.dto.BooleanQuestionDto;
import org.broadinstitute.ddp.db.dto.CompositeQuestionDto;
import org.broadinstitute.ddp.db.dto.DateQuestionDto;
import org.broadinstitute.ddp.db.dto.NumericQuestionDto;
import org.broadinstitute.ddp.db.dto.PicklistQuestionDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.TextQuestionDto;
import org.broadinstitute.ddp.model.activity.definition.question.DatePicklistDef;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.result.LinkedHashMapRowReducer;
import org.jdbi.v3.core.result.RowView;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.customizer.BindList.EmptyHandling;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.statement.UseRowReducer;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface JdbiQuestion extends SqlObject {

    @SqlUpdate("insert into question (question_type_id, is_restricted, question_stable_code_id,"
            + " question_prompt_template_id, tooltip_template_id, info_header_template_id, info_footer_template_id,"
            + " revision_id, study_activity_id, hide_number, is_deprecated)"
            + " values(:questionTypeId, :isRestricted, :stableCodeId, :promptTemplateId, :tooltipTemplateId,"
            + " :infoHeaderTemplateId, :infoFooterTemplateId, :revisionId, :activityId,"
            + " :hideNumber, :isDeprecated)")
    @GetGeneratedKeys
    long insert(@Bind("questionTypeId") long questionTypeId, @Bind("isRestricted") boolean isRestricted,
                @Bind("stableCodeId") long stableCodeId, @Bind("promptTemplateId") long promptTemplateId,
                @Bind("tooltipTemplateId") Long tooltipTemplateId,
                @Bind("infoHeaderTemplateId") Long infoHeaderTemplateId, @Bind("infoFooterTemplateId") Long infoFooterTemplateId,
                @Bind("revisionId") long revisionId, @Bind("activityId") long activityId,
                @Bind("hideNumber") boolean hideNumber, @Bind("isDeprecated") boolean isDeprecated);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryQuestionDtoByStableIdAndInstanceGuid")
    @RegisterConstructorMapper(QuestionDto.class)
    Optional<QuestionDto> findDtoByStableIdAndInstanceGuid(@Bind("stableId") String stableId,
                                                           @Bind("instanceGuid") String instanceGuid);

    default Optional<QuestionDto> findDtoByStableIdAndInstance(String stableId, ActivityInstanceDto instance) {
        return findDtoByStableIdAndInstanceGuid(stableId, instance.getGuid());
    }

    @UseStringTemplateSqlLocator
    @SqlQuery("queryQuestionsDtoByActivityId")
    @RegisterConstructorMapper(QuestionDto.class)
    List<QuestionDto> findDtosByActivityId(Long activityId);

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
    @SqlQuery("queryLatestDtoByStudyIdAndQuestionStableId")
    @RegisterConstructorMapper(QuestionDto.class)
    Optional<QuestionDto> findLatestDtoByStudyIdAndQuestionStableId(
            @Bind("studyId") long studyId,
            @Bind("questionStableId") String questionStableId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryLatestDtosByStudyIdAndQuestionStableIds")
    @RegisterConstructorMapper(QuestionDto.class)
    Stream<QuestionDto> findLatestDtosByStudyIdAndQuestionStableIds(
            @Bind("studyId") long studyId,
            @BindList(value = "questionStableIds", onEmpty = EmptyHandling.NULL) Set<String> questionStableId);

    @SqlUpdate("update question set revision_id = :revisionId where question_id = :questionId")
    int updateRevisionIdById(@Bind("questionId") long questionId, @Bind("revisionId") long revisionId);

    @SqlUpdate("update question set is_deprecated = :isDeprecated where question_id = :questionId")
    int updateIsDeprecatedById(@Bind("questionId") long questionId, @Bind("isDeprecated") boolean isDeprecated);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryQuestionDtosByIds")
    @RegisterConstructorMapper(AgreementQuestionDto.class)
    @RegisterConstructorMapper(BooleanQuestionDto.class)
    @RegisterConstructorMapper(DateQuestionDto.class)
    @RegisterConstructorMapper(NumericQuestionDto.class)
    @RegisterConstructorMapper(PicklistQuestionDto.class)
    @RegisterConstructorMapper(TextQuestionDto.class)
    @RegisterConstructorMapper(CompositeQuestionDto.class)
    @RegisterRowMapper(DatePicklistDefMapper.class)
    @UseRowReducer(QuestionDtoReducer.class)
    Stream<QuestionDto> findQuestionDtosByIds(
            @BindList(value = "questionIds", onEmpty = EmptyHandling.NULL) Set<Long> questionIds);

    default Optional<QuestionDto> findQuestionDtoById(long questionId) {
        return findQuestionDtosByIds(Set.of(questionId)).findFirst();
    }

    class QuestionDtoReducer implements LinkedHashMapRowReducer<Long, QuestionDto> {
        @Override
        public void accumulate(Map<Long, QuestionDto> container, RowView view) {
            QuestionDto questionDto;
            var type = QuestionType.valueOf(view.getColumn("question_type", String.class));
            switch (type) {
                case AGREEMENT:
                    questionDto = view.getRow(AgreementQuestionDto.class);
                    break;
                case BOOLEAN:
                    questionDto = view.getRow(BooleanQuestionDto.class);
                    break;
                case DATE:
                    questionDto = view.getRow(DateQuestionDto.class);
                    break;
                case NUMERIC:
                    questionDto = view.getRow(NumericQuestionDto.class);
                    break;
                case PICKLIST:
                    questionDto = view.getRow(PicklistQuestionDto.class);
                    break;
                case TEXT:
                    questionDto = view.getRow(TextQuestionDto.class);
                    break;
                case COMPOSITE:
                    questionDto = view.getRow(CompositeQuestionDto.class);
                    break;
                default:
                    throw new DaoException("Unhandled question type: " + type);
            }
            container.put(questionDto.getId(), questionDto);
        }
    }

    class DatePicklistDefMapper implements RowMapper<DatePicklistDef> {
        @Override
        public DatePicklistDef map(ResultSet rs, StatementContext ctx) throws SQLException {
            Boolean useMonthNames = null;
            Integer yearsForward = null;
            Integer yearsBack = null;
            Integer yearAnchor = null;
            Integer firstSelectedYear = null;

            // Grab month picklist data if available
            Long monthIndicator = (Long) rs.getObject("dqmp_id");
            if (monthIndicator != null) {
                useMonthNames = rs.getBoolean(SqlConstants.DateQuestionMonthPicklistTable.USE_MONTH_NAMES);
            }

            // Grab year picklist data if available
            Long yearIndicator = (Long) rs.getObject("dqyp_id");
            boolean allowFutureYears = false;
            if (yearIndicator != null) {
                yearsForward = (Integer) rs.getObject(SqlConstants.DateQuestionYearPicklistTable.YEARS_FORWARD);
                yearsBack = (Integer) rs.getObject(SqlConstants.DateQuestionYearPicklistTable.YEARS_BACK);
                yearAnchor = (Integer) rs.getObject(SqlConstants.DateQuestionYearPicklistTable.YEAR_ANCHOR);
                firstSelectedYear = (Integer) rs.getObject(SqlConstants.DateQuestionYearPicklistTable.FIRST_SELECTED_YEAR);
                allowFutureYears = rs.getBoolean(SqlConstants.DateQuestionYearPicklistTable.ALLOW_FUTURE_YEARS);
            }

            return new DatePicklistDef(useMonthNames, yearsForward, yearsBack, yearAnchor, firstSelectedYear, allowFutureYears);
        }
    }
}
