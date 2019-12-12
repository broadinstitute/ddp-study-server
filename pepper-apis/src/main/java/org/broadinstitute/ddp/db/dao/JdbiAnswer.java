package org.broadinstitute.ddp.db.dao;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.AnswerDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface JdbiAnswer extends SqlObject {

    Logger LOG = LoggerFactory.getLogger(JdbiAnswer.class);

    @SqlQuery("select answer_id from answer where operator_user_id = :userId")
    List<Long> getAnswerIds(long userId);

    @CreateSqlObject
    JdbiPicklistOptionAnswer getPicklistOptionAnswer();

    @CreateSqlObject
    JdbiBooleanAnswer getBooleanAnswer();

    @CreateSqlObject
    JdbiTextAnswer getTextAnswer();

    @SqlQuery("select ans.answer_id"
            + "  from answer as ans"
            + "  join activity_instance as ai on ai.activity_instance_id = ans.activity_instance_id"
            + " where ans.question_id = :questionId"
            + "   and ai.activity_instance_guid = :instanceGuid"
    )
    List<Long> findAnswerIdsByInstanceGuidAndQuestionId(@Bind("instanceGuid") String activityInstanceGuid,
                                                        @Bind("questionId") long questionId);


    @SqlUpdate("delete from answer where question_id = :questionId and activity_instance_id = :activityInstanceId")
    int deleteAllAnswersForQuestion(long activityInstanceId, long questionId);

    /**
     * Deletes all answers for the given question in the given activity instance.
     */
    default int deleteAllAnswersForQuestion(ActivityInstanceDto activityInstanceDto, QuestionDto questionDto) {
        // first delete subclass tables

        boolean unsupportedType = false;
        switch (questionDto.getType()) {
            case PICKLIST:
                JdbiPicklistOptionAnswer picklistOptionAnswer = getPicklistOptionAnswer();
                long optionRowsDeleted = picklistOptionAnswer.deleteAllForQuestion(
                        activityInstanceDto.getId(), questionDto.getId());
                LOG.info("Deleted {} answer option rows for question {}", optionRowsDeleted, questionDto.getStableId());
                break;
            case BOOLEAN:
                JdbiBooleanAnswer booleanAnswer = getBooleanAnswer();
                int numBooleanRowsDeleted = booleanAnswer.deleteAllAnswerAnswersForQuestion(
                        activityInstanceDto.getId(), questionDto.getId());
                LOG.info("Deleted {} {} rows for boolean question {}", numBooleanRowsDeleted,
                        questionDto.getType(), questionDto.getStableId());
                break;
            case TEXT:
                int numTextRowsDeleted = getTextAnswer().deleteAllAnswerValuesForQuestion(
                        activityInstanceDto.getId(), questionDto.getId()
                );
                LOG.info("Deleted {} {} rows for text question {}", numTextRowsDeleted,
                        questionDto.getType(), questionDto.getStableId());
                break;
            default:
                unsupportedType = true;

        }
        if (unsupportedType) {
            throw new DaoException("Delete for " + questionDto.getType() + " has not been implemented yet."
                    + "  How about you write it?");
        }
        // now delete parent rows
        int numAnswerRowsDeleted = deleteAllAnswersForQuestion(activityInstanceDto.getId(),
                questionDto.getId());
        LOG.info("Deleted {} answer rows for {}", numAnswerRowsDeleted, questionDto.getStableId());
        return numAnswerRowsDeleted;
    }

    @SqlUpdate("insert into answer(question_id, operator_user_id, activity_instance_id, created_at,"
            + "last_updated_at, answer_guid) (select :questionId, u.user_id,:activityInstanceId,"
            + ":creationTimeEpoch,:updateTimeEpoch,:answerGuid from user u where u.guid = :operatorGuid)")
    @GetGeneratedKeys
    long insert(long questionId, String operatorGuid, long activityInstanceId, long creationTimeEpoch,
                long updateTimeEpoch, String answerGuid);

    default long insertBaseAnswer(long questionId, String operatorGuid, long activityInstanceId) {
        long timeEpoch = Instant.now().toEpochMilli();
        String guid = DBUtils.uniqueStandardGuid(getHandle(), "answer", "answer_guid");

        return insert(questionId, operatorGuid, activityInstanceId, timeEpoch, timeEpoch, guid);
    }

    @SqlQuery("select a.answer_id, a.answer_guid, q.question_id,"
            + "       qsc.stable_id as question_stable_id, qt.question_type_code as question_type"
            + " from answer as a"
            + " join question as q on q.question_id = a.question_id"
            + " join question_stable_code as qsc on qsc.question_stable_code_id = q.question_stable_code_id"
            + " join question_type as qt on qt.question_type_id = q.question_type_id"
            + " where a.answer_id = :id")
    @RegisterConstructorMapper(AnswerDto.class)
    Optional<AnswerDto> findDtoById(@Bind("id") long id);

    @SqlQuery("select a.answer_id, a.answer_guid, q.question_id,"
            + "       qsc.stable_id as question_stable_id, qt.question_type_code as question_type"
            + " from answer as a"
            + " join question as q on q.question_id = a.question_id"
            + " join question_stable_code as qsc on qsc.question_stable_code_id = q.question_stable_code_id"
            + " join question_type as qt on qt.question_type_id = q.question_type_id"
            + " where a.answer_guid = :guid")
    @RegisterConstructorMapper(AnswerDto.class)
    Optional<AnswerDto> findDtoByGuid(@Bind("guid") String guid);

}
