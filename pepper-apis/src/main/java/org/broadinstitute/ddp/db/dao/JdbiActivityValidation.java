package org.broadinstitute.ddp.db.dao;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.content.I18nContentRenderer;
import org.broadinstitute.ddp.db.dto.ActivityValidationDto;

import org.jdbi.v3.core.result.LinkedHashMapRowReducer;
import org.jdbi.v3.core.result.RowView;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.statement.UseRowReducer;

public interface JdbiActivityValidation extends SqlObject {

    @CreateSqlObject
    JdbiActivityValidationAffectedQuestionStableIds getJdbiActivityValidationAffectedQuestionStableIds();

    @CreateSqlObject
    JdbiRevision getJdbiRevision();

    @CreateSqlObject
    TemplateDao getTemplateDao();

    default int _insert(ActivityValidationDto dto, long userId, long umbrellaStudyId, long errorMessageTemplateRevisionId) {
        long errorMessageTemplateId = getTemplateDao().insertTemplate(dto.getErrorMessageTemplate(), errorMessageTemplateRevisionId);

        int activityValidationId = _insertActivityValidation(
                dto.getStudyActivityId(),
                dto.getPreconditionText(),
                dto.getExpressionText(),
                errorMessageTemplateId
        );
        JdbiActivityValidationAffectedQuestionStableIds jdbiActivityValidationAffectedQuestionStableIds
                = getJdbiActivityValidationAffectedQuestionStableIds();
        jdbiActivityValidationAffectedQuestionStableIds._insertAffectedQuestionStableIdsForValidation(
                activityValidationId, dto.getAffectedQuestionStableIds(), umbrellaStudyId
        );
        return activityValidationId;
    }

    @SqlUpdate(
            "INSERT INTO activity_validation(study_activity_id, precondition_text, expression_text, error_message_template_id)"
            + " VALUES (:studyActivityId, :preconditionText, :expressionText, :errorMessageTemplateId)"
    )
    @GetGeneratedKeys
    int _insertActivityValidation(
            @Bind("studyActivityId") long studyActivityId,
            @Bind("preconditionText") String preconditionText,
            @Bind("expressionText") String expressionText,
            @Bind("errorMessageTemplateId") long errorMessageTemplateId
    );

    default List<ActivityValidationDto> _findByActivityIdTranslated(long activityId, long isoLanguageCode) {
        List<ActivityValidationDto> validations = _findByActivityId(activityId);
        I18nContentRenderer i18nContentRenderer = new I18nContentRenderer();
        validations.forEach(
                validation -> validation.setErrorMessage(
                        i18nContentRenderer.renderContent(getHandle(), validation.getErrorMessageTemplateId(), isoLanguageCode)
                )
        );
        return validations;
    }

    @SqlQuery(
            "SELECT sa.study_activity_id AS av_study_activity_id, av.activity_validation_id AS av_activity_validation_id,"
            + " av.precondition_text AS av_precondition_text, av.expression_text AS av_expression_text,"
            + " av.error_message_template_id AS av_error_message_template_id,"
            + " qsc.stable_id AS qsc_affected_question_stable_id"
            + " FROM study_activity sa JOIN activity_validation av ON sa.study_activity_id = av.study_activity_id"
            + " JOIN activity_validation_affected_question avaf ON av.activity_validation_id = avaf.activity_validation_id"
            + " JOIN question q ON avaf.question_id = q.question_id"
            + " JOIN question_stable_code qsc ON q.question_stable_code_id = qsc.question_stable_code_id"
            + " WHERE sa.study_activity_id = :activityId"
    )
    @RegisterConstructorMapper(value = ActivityValidationDto.class, prefix = "av")
    @UseRowReducer(ActivityValidationDtoReducer.class)
    /**
     * Finds all validations associated with a given activity
     * Query column names contain prefixes that are used by JDBI to infer which of them are fields of a certain class
     * A row reducer is used to handle a one-to-many relation by accumulating child entities
     *
     */
    List<ActivityValidationDto> _findByActivityId(@Bind("activityId") long activityId);

    default int _deleteByActivityId(long activityId) {
        List<ActivityValidationDto> activityValidations = _findByActivityId(activityId);
        List<Long> validationIdsForActivity = activityValidations
                .stream()
                .map(dto -> dto.getActivityValidationId())
                .collect(Collectors.toList());
        return _deleteValidationsByActivityId(activityId);
    }

    @SqlUpdate(
            "DELETE FROM activity_validation WHERE study_activity_id = :activityId"
    )
    int _deleteValidationsByActivityId(@Bind("activityId") long activityId);

    class ActivityValidationDtoReducer implements LinkedHashMapRowReducer<Long, ActivityValidationDto> {
        @Override
        public void accumulate(Map<Long, ActivityValidationDto> map, RowView rowView) {
            ActivityValidationDto actValidDto = map.computeIfAbsent(
                    rowView.getColumn("av_activity_validation_id", Long.class),
                    id -> rowView.getRow(ActivityValidationDto.class)
            );
            if (rowView.getColumn("qsc_affected_question_stable_id", String.class) != null) {
                actValidDto.addAffectedField(
                        rowView.getColumn("qsc_affected_question_stable_id", String.class)
                );
            }
        }
    }

}
