package org.broadinstitute.ddp.db.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.broadinstitute.ddp.constants.SqlConstants;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dto.AgreementQuestionDto;
import org.broadinstitute.ddp.db.dto.BooleanQuestionDto;
import org.broadinstitute.ddp.db.dto.CompositeQuestionDto;
import org.broadinstitute.ddp.db.dto.DateQuestionDto;
import org.broadinstitute.ddp.db.dto.FileQuestionDto;
import org.broadinstitute.ddp.db.dto.NumericQuestionDto;
import org.broadinstitute.ddp.db.dto.DecimalQuestionDto;
import org.broadinstitute.ddp.db.dto.EquationQuestionDto;
import org.broadinstitute.ddp.db.dto.PicklistQuestionDto;
import org.broadinstitute.ddp.db.dto.MatrixQuestionDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.TextQuestionDto;
import org.broadinstitute.ddp.db.dto.ActivityInstanceSelectQuestionDto;
import org.broadinstitute.ddp.model.activity.definition.question.DatePicklistDef;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;
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
            + " revision_id, study_activity_id, hide_number, is_deprecated, is_write_once)"
            + " values(:questionTypeId, :isRestricted, :stableCodeId, :promptTemplateId, :tooltipTemplateId,"
            + " :infoHeaderTemplateId, :infoFooterTemplateId, :revisionId, :activityId,"
            + " :hideNumber, :isDeprecated, :isWriteOnce)")
    @GetGeneratedKeys
    long insert(@Bind("questionTypeId") long questionTypeId, @Bind("isRestricted") boolean isRestricted,
                @Bind("stableCodeId") long stableCodeId, @Bind("promptTemplateId") long promptTemplateId,
                @Bind("tooltipTemplateId") Long tooltipTemplateId,
                @Bind("infoHeaderTemplateId") Long infoHeaderTemplateId, @Bind("infoFooterTemplateId") Long infoFooterTemplateId,
                @Bind("revisionId") long revisionId, @Bind("activityId") long activityId,
                @Bind("hideNumber") boolean hideNumber, @Bind("isDeprecated") boolean isDeprecated,
                @Bind("isWriteOnce") boolean isWriteOnce);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryDtoByActivityIdAndQuestionStableId")
    @RegisterConstructorMapper(QuestionDto.class)
    Optional<QuestionDto> findDtoByActivityIdAndQuestionStableId(
            @Bind("activityId") long activityId,
            @Bind("questionStableId") String questionStableId);

    // study-builder
    @UseStringTemplateSqlLocator
    @SqlQuery("queryLatestDtoByStudyIdAndQuestionStableId")
    @RegisterConstructorMapper(QuestionDto.class)
    Optional<QuestionDto> findLatestDtoByStudyIdAndQuestionStableId(
            @Bind("studyId") long studyId,
            @Bind("questionStableId") String questionStableId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryLatestDtoByStudyGuidAndQuestionStableId")
    @RegisterConstructorMapper(QuestionDto.class)
    Optional<QuestionDto> findLatestDtoByStudyGuidAndQuestionStableId(
            @Bind("studyGuid") String studyGuid,
            @Bind("questionStableId") String questionStableId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryQuestionsByStudyGuid")
    @RegisterConstructorMapper(QuestionDto.class)
    List<QuestionDto> findByStudyGuid(@Bind("studyGuid") String studyGuid);

    @SqlQuery("select study_activity_code from activity_instance_select_activity_code where"
            + " activity_instance_select_question_id = :questionId")
    List<String> getActivityCodesByActivityInstanceSelectQuestionId(@Bind("questionId") Long questionId);

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
    @SqlQuery("queryQuestionIdByStableIdAndInstanceGuid")
    Optional<Long> findIdByStableIdAndInstanceGuid(@Bind("stableId") String stableId,
                                                   @Bind("instanceGuid") String instanceGuid);

    default Optional<QuestionDto> findDtoByStableIdAndInstanceGuid(String stableId, String instanceGuid) {
        return findIdByStableIdAndInstanceGuid(stableId, instanceGuid)
                .flatMap(this::findQuestionDtoById);
    }

    @SqlQuery("select suggestion"
            + "  from text_question_suggestion"
            + " where text_question_id = :questionId"
            + " order by display_order, suggestion")
    List<String> findTextQuestionSuggestions(@Bind("questionId") long questionId);

    @SqlQuery("select parent_question_id"
            + "  from composite_question__question"
            + " where child_question_id = :childId")
    Optional<Long> findCompositeParentIdByChildId(@Bind("childId") long childQuestionId);

    default Optional<CompositeQuestionDto> findCompositeParentDtoByChildId(long childQuestionId) {
        return findCompositeParentIdByChildId(childQuestionId)
                .flatMap(this::findQuestionDtoById)
                .map(dto -> (CompositeQuestionDto) dto);
    }

    default List<Long> findCompositeChildIdsByParentIdAndInstanceGuid(long parentQuestionId, String instanceGuid) {
        return collectOrderedCompositeChildIdsByParentIdsAndInstanceGuid(Set.of(parentQuestionId), instanceGuid)
                .getOrDefault(parentQuestionId, new ArrayList<>());
    }

    default Map<Long, List<Long>> collectOrderedCompositeChildIdsByParentIdsAndInstanceGuid(Iterable<Long> parentQuestionIds,
                                                                                            String instanceGuid) {
        Map<Long, List<Long>> parentIdToChildIds = new HashMap<>();
        try (var stream = findOrderedCompositeChildIdsByParentIdsAndInstanceGuid(parentQuestionIds, instanceGuid)) {
            stream.forEach(pair -> parentIdToChildIds
                    .computeIfAbsent(pair.getParentId(), id -> new ArrayList<>())
                    .add(pair.getChildId()));
        }
        return parentIdToChildIds;
    }

    @SqlQuery("select cqq.parent_question_id, cqq.child_question_id"
            + "  from composite_question__question as cqq"
            + "  join question as q on q.question_id = cqq.child_question_id"
            + "  join revision as rev on rev.revision_id = q.revision_id"
            + "  join activity_instance as ai on ai.study_activity_id = q.study_activity_id"
            + " where cqq.parent_question_id in (<parentIds>)"
            + "   and ai.activity_instance_guid = :instanceGuid"
            + "   and rev.start_date <= ai.created_at"
            + "   and (rev.end_date is null or ai.created_at < rev.end_date)")
    @RegisterConstructorMapper(CompositeIdPair.class)
    Stream<CompositeIdPair> findOrderedCompositeChildIdsByParentIdsAndInstanceGuid(
            @BindList(value = "parentIds", onEmpty = EmptyHandling.NULL) Iterable<Long> parentQuestionIds,
            @Bind("instanceGuid") String instanceGuid);

    default Map<Long, List<Long>> collectOrderedCompositeChildIdsByParentIdsAndTimestamp(Iterable<Long> parentQuestionIds, long timestamp) {
        Map<Long, List<Long>> parentIdToChildIds = new HashMap<>();
        try (var stream = findOrderedCompositeChildIdsByParentIdsAndTimestamp(parentQuestionIds, timestamp)) {
            stream.forEach(pair -> parentIdToChildIds
                    .computeIfAbsent(pair.getParentId(), id -> new ArrayList<>())
                    .add(pair.getChildId()));
        }
        return parentIdToChildIds;
    }

    @SqlUpdate("DELETE FROM question WHERE question_id = :questionId")
    boolean deleteBaseQuestion(@Bind("questionId") long questionId);

    @SqlQuery("select cqq.parent_question_id, cqq.child_question_id"
            + "  from composite_question__question as cqq"
            + "  join question as q on q.question_id = cqq.child_question_id"
            + "  join revision as rev on rev.revision_id = q.revision_id"
            + " where cqq.parent_question_id in (<parentIds>)"
            + "   and rev.start_date <= :timestamp"
            + "   and (rev.end_date is null or :timestamp < rev.end_date)"
            + " order by cqq.parent_question_id asc, cqq.display_order asc")
    @RegisterConstructorMapper(CompositeIdPair.class)
    Stream<CompositeIdPair> findOrderedCompositeChildIdsByParentIdsAndTimestamp(
            @BindList(value = "parentIds", onEmpty = EmptyHandling.NULL) Iterable<Long> parentQuestionIds,
            @Bind("timestamp") long timestamp);


    class CompositeIdPair {
        private long parentId;
        private long childId;

        @JdbiConstructor
        public CompositeIdPair(
                @ColumnName("parent_question_id") long parentId,
                @ColumnName("child_question_id") long childId) {
            this.parentId = parentId;
            this.childId = childId;
        }

        public long getParentId() {
            return parentId;
        }

        public long getChildId() {
            return childId;
        }
    }

    @UseStringTemplateSqlLocator
    @SqlQuery("queryQuestionDtosByIds")
    @RegisterConstructorMapper(AgreementQuestionDto.class)
    @RegisterConstructorMapper(BooleanQuestionDto.class)
    @RegisterConstructorMapper(DateQuestionDto.class)
    @RegisterConstructorMapper(FileQuestionDto.class)
    @RegisterConstructorMapper(NumericQuestionDto.class)
    @RegisterConstructorMapper(DecimalQuestionDto.class)
    @RegisterConstructorMapper(PicklistQuestionDto.class)
    @RegisterConstructorMapper(MatrixQuestionDto.class)
    @RegisterConstructorMapper(TextQuestionDto.class)
    @RegisterConstructorMapper(ActivityInstanceSelectQuestionDto.class)
    @RegisterConstructorMapper(CompositeQuestionDto.class)
    @RegisterConstructorMapper(EquationQuestionDto.class)
    @RegisterRowMapper(DatePicklistDefMapper.class)
    @UseRowReducer(QuestionDtoReducer.class)
    Stream<QuestionDto> findQuestionDtosByIds(
            @BindList(value = "questionIds", onEmpty = EmptyHandling.NULL) Iterable<Long> questionIds);

    default Optional<QuestionDto> findQuestionDtoById(long questionId) {
        try (var stream = findQuestionDtosByIds(Set.of(questionId))) {
            return stream.findFirst();
        }
    }

    @UseStringTemplateSqlLocator
    @RegisterConstructorMapper(QuestionDto.class)
    @SqlQuery("select_basic_question_dtos_by_id")
    Optional<QuestionDto> findBasicQuestionDtoById(long questionId);

    @SqlUpdate("insert into file_question (question_id, max_file_size) values (:questionId, :maxFileSize)")
    int insertFileQuestion(@Bind("questionId") long questionId, @Bind("maxFileSize") long maxFileSize);

    @SqlUpdate("insert into mime_type (mime_type_code) values (:mimeTypeCode)")
    @GetGeneratedKeys
    long insertMimeType(@Bind("mimeTypeCode") String mimeTypeCode);

    @SqlUpdate("insert into file_question__mime_type (file_question_id, mime_type_id) values (:fileQuestionId, :mimeTypeId)")
    int insertFileQuestionMimeType(@Bind("fileQuestionId") long fileQuestionId, @Bind("mimeTypeId") long mimeTypeId);

    @SqlQuery("select mime_type_id from mime_type where mime_type_code = :mime_type_code")
    Optional<Long> findMimeTypeIdByMimeType(@Bind("mime_type_code") String mimeTypeCode);

    default long findMimeTypeIdOrInsert(String mimeType) {
        Optional<Long> mimeTypeId = findMimeTypeIdByMimeType(mimeType);
        if (mimeTypeId.isPresent()) {
            return mimeTypeId.get();
        } else {
            return insertMimeType(mimeType);
        }
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
                case FILE:
                    questionDto = view.getRow(FileQuestionDto.class);
                    break;
                case NUMERIC:
                    questionDto = view.getRow(NumericQuestionDto.class);
                    break;
                case DECIMAL:
                    questionDto = view.getRow(DecimalQuestionDto.class);
                    break;
                case PICKLIST:
                    questionDto = view.getRow(PicklistQuestionDto.class);
                    break;
                case MATRIX:
                    questionDto = view.getRow(MatrixQuestionDto.class);
                    break;
                case TEXT:
                    questionDto = view.getRow(TextQuestionDto.class);
                    break;
                case ACTIVITY_INSTANCE_SELECT:
                    questionDto = view.getRow(ActivityInstanceSelectQuestionDto.class);
                    break;
                case COMPOSITE:
                    questionDto = view.getRow(CompositeQuestionDto.class);
                    break;
                case EQUATION:
                    questionDto = view.getRow(EquationQuestionDto.class);
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
