package org.broadinstitute.ddp.db.dao;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.AgreementAnswerDto;
import org.broadinstitute.ddp.db.dto.AnswerDto;
import org.broadinstitute.ddp.db.dto.CompositeAnswerSummaryDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.model.activity.instance.answer.AgreementAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface AnswerDao extends SqlObject {

    Logger LOG = LoggerFactory.getLogger(AnswerDao.class);

    @CreateSqlObject
    JdbiPicklistOptionAnswer getJdbiPicklistOptionAnswer();

    @CreateSqlObject
    JdbiBooleanAnswer getJdbiBooleanAnswer();

    @CreateSqlObject
    PicklistAnswerDao getPicklistAnswerDao();

    @CreateSqlObject
    JdbiTextAnswer getJdbiTextAnswer();

    @CreateSqlObject
    JdbiDateAnswer getJdbiDateAnswer();

    @CreateSqlObject
    JdbiNumericAnswer getJdbiNumericAnswer();

    @CreateSqlObject
    JdbiAgreementAnswer getJdbiAgreementAnswer();

    @CreateSqlObject
    JdbiCompositeAnswer getJdbiCompositeAnswer();

    @CreateSqlObject
    JdbiAnswer getJdbiAnswer();

    /**
     * Finds the answer id to specified question in the last-created activity instance of the specified activity. Useful in cases where we
     * just need to know if there is an answer or not instead of needing all the data about the answer itself.
     *
     * @param userGuid         the user guid
     * @param activityId       the activity id
     * @param questionStableId the question stable id
     * @return answer id if exists, empty otherwise
     */
    @UseStringTemplateSqlLocator
    @SqlQuery("queryAnswerIdForQuestionInLatestInstance")
    Optional<Long> findAnswerIdForQuestionInLatestInstance(
            @Bind("userGuid") String userGuid,
            @Bind("activityId") long activityId,
            @Bind("questionStableId") String questionStableId);

    @SqlQuery(" select da.year, da.month, da.day "
            + " from activity_instance as ai "
            + " join answer as a on ai.activity_instance_id = a.activity_instance_id "
            + " join date_answer as da on a.answer_id = da.answer_id "
            + " join user as u on ai.participant_id = u.user_id "
            + " join question as q on a.question_id = q.question_id "
            + " join question_stable_code as qsc on qsc.question_stable_code_id = q.question_stable_code_id "
            + " join umbrella_study as us on qsc.umbrella_study_id = us.umbrella_study_id "
            + " join revision as rev on rev.revision_id = q.revision_id "
            + " where u.user_id = :userId "
            + " and rev.start_date <= ai.created_at "
            + " and (rev.end_date is null or ai.created_at < rev.end_date) "
            + " and qsc.stable_id = :questionStableId "
            + " and us.umbrella_study_id = :studyId "
            + " order by ai.created_at desc limit 1")
    @RegisterConstructorMapper(DateValue.class)
    Optional<DateValue> findLatestDateAnswerByQuestionStableIdAndUserId(@Bind("questionStableId") String questionStableId,
                                                                        @Bind("userId") long userId,
                                                                        @Bind("studyId") long studyId);

    @SqlQuery("select qt.question_type_code"
            + "  from answer as a"
            + "  join question as q on a.question_id = q.question_id"
            + "  join question_type as qt on qt.question_type_id = q.question_type_id"
            + " where a.answer_id = :answerId"
    )
    QuestionType findQuestionTypeByAnswerId(@Bind("answerId") long answerId);

    /**
     * Deletes all answers for the given question in the given activity instance.
     *
     * @return
     */
    default int deleteAllAnswersForQuestion(ActivityInstanceDto activityInstanceDto, QuestionDto questionDto) {
        // first delete subclass tables

        boolean unsupportedType = false;
        switch (questionDto.getType()) {
            case PICKLIST:
                JdbiPicklistOptionAnswer picklistOptionAnswer = getJdbiPicklistOptionAnswer();
                long optionRowsDeleted = picklistOptionAnswer.deleteAllForQuestion(
                        activityInstanceDto.getId(), questionDto.getId());
                LOG.info("Deleted {} answer option rows for question {}", optionRowsDeleted, questionDto.getStableId());
                break;
            case BOOLEAN:
                JdbiBooleanAnswer booleanAnswer = getJdbiBooleanAnswer();
                int numRowsDeleted = booleanAnswer.deleteAllAnswerAnswersForQuestion(
                        activityInstanceDto.getId(), questionDto.getId());
                LOG.info("Deleted {} {} rows for question {}",
                        numRowsDeleted, questionDto.getType(), questionDto.getStableId());
                break;
            default:
                unsupportedType = true;

        }
        if (unsupportedType) {
            throw new DaoException("Delete for " + questionDto.getType() + " has not been implemented yet. "
                    + "How about you write it?");
        }
        // now delete parent rows
        int numAnswerRowsDeleted = getJdbiAnswer().deleteAllAnswersForQuestion(
                activityInstanceDto.getId(), questionDto.getId());
        LOG.info("Deleted {} answer rows for {}", numAnswerRowsDeleted, questionDto.getStableId());
        return numAnswerRowsDeleted;
    }

    /**
     * Get answer by id. Automatically looks up type.
     *
     * @param answerId the answer id
     * @return single answer
     */
    default Answer getAnswerById(long answerId) {
        QuestionType type = findQuestionTypeByAnswerId(answerId);
        return getAnswerByIdAndType(answerId, type);
    }

    /**
     * Get answer by id.
     *
     * @param answerId     the answer id
     * @param questionType the question type the answer is responding to
     * @return single answer
     */
    default Answer getAnswerByIdAndType(long answerId, QuestionType questionType) {
        switch (questionType) {
            case BOOLEAN:
                return getJdbiBooleanAnswer().findByAnswerId(answerId);
            case PICKLIST:
                return getPicklistAnswerDao()
                        .findByAnswerId(answerId)
                        .orElseThrow(() -> new DaoException("Could not find picklist answer with id " + answerId));
            case TEXT:
                return getJdbiTextAnswer().findByAnswerId(answerId);
            case DATE:
                return getJdbiDateAnswer().getById(answerId).orElseThrow(
                        () -> new DaoException("Could not find date answer with id " + answerId));
            case NUMERIC:
                return getJdbiNumericAnswer().findById(answerId).orElseThrow(
                        () -> new DaoException("Could not find numeric answer with id " + answerId));
            case AGREEMENT:
                AgreementAnswerDto dto = getJdbiAgreementAnswer().findDtoById(answerId).orElseThrow(
                        () -> new DaoException("Could not find agreement answer with id " + answerId));
                return new AgreementAnswer(answerId, dto.getQuestionStableId(), dto.getAnswerGuid(), dto.getAnswer());
            case COMPOSITE:
                Optional<CompositeAnswerSummaryDto> optionalAnswerSummary =
                        getJdbiCompositeAnswer().findCompositeAnswerSummary(answerId);
                if (optionalAnswerSummary.isPresent()) {
                    CompositeAnswerSummaryDto summaryObj = optionalAnswerSummary.get();
                    CompositeAnswer answer = new CompositeAnswer(summaryObj.getId(), summaryObj.getQuestionStableId(),
                            summaryObj.getGuid());
                    summaryObj.getChildAnswers().forEach((List<AnswerDto> rowChildDtos) -> {
                        List<Answer> rowOfAnswers = rowChildDtos.stream()
                                .map(childAnswerDto ->
                                        //updated query gives us the question information if row exists but answer does not
                                        //check for null then
                                        childAnswerDto.getId() == null ? null : getAnswerById(childAnswerDto.getId()))
                                .collect(toList());
                        answer.addRowOfChildAnswers(rowOfAnswers);
                    });
                    return answer;
                } else {
                    throw new DaoException("Unable to find CompositeAnswer with id" + answerId);
                }
            default:
                throw new DaoException("Unhandled question type " + questionType);
        }
    }

    @UseStringTemplateSqlLocator
    @SqlUpdate("deleteAllAnswerValuesByActivityInstanceIds")
    int _deleteAllAnswerValuesByInstanceIds(@BindList(value = "instanceIds", onEmpty = BindList.EmptyHandling.NULL) Set<Long> instanceIds);

    @SqlUpdate("delete from answer where activity_instance_id in (<instanceIds>)")
    int _deleteAllAnswersByInstanceIds(@BindList(value = "instanceIds", onEmpty = BindList.EmptyHandling.NULL) Set<Long> instanceIds);

    default int deleteAllByInstanceIds(Set<Long> instanceIds) {
        _deleteAllAnswerValuesByInstanceIds(instanceIds);
        return _deleteAllAnswersByInstanceIds(instanceIds);
    }
}
