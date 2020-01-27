package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface AnswerSql extends SqlObject {

    @GetGeneratedKeys
    @SqlUpdate("insert into answer (answer_guid, operator_user_id, activity_instance_id, created_at, last_updated_at, question_id)"
            + " values (:guid, :operatorId, :instanceId, :createdAt, :updatedAt, :questionId)")
    long insertAnswer(@Bind("guid") String answerGuid,
                      @Bind("operatorId") long operatorId,
                      @Bind("instanceId") long instanceId,
                      @Bind("questionId") long questionId,
                      @Bind("createdAt") long createdAtMillis,
                      @Bind("updatedAt") long updatedAtMillis);

    @GetGeneratedKeys
    @SqlUpdate("insert into answer (answer_guid, operator_user_id, activity_instance_id, created_at, last_updated_at, question_id)"
            + " values (:guid, :operatorId, :instanceId, :createdAt, :updatedAt,"
            + "        (select question_id from question as q"
            + "           join question_stable_code as qsc on q.question_stable_code_id = qsc.question_stable_code_id"
            + "           join revision as rev on q.revision_id = rev.revision_id"
            + "           join activity_instance as ai on q.study_activity_id = ai.study_activity_id"
            + "          where qsc.stable_id = :questionStableId"
            + "            and ai.activity_instance_id = :instanceId"
            + "            and rev.start_date <= ai.created_at"
            + "            and (rev.end_date is null or ai.created_at < rev.end_date)))")
    long insertAnswerByQuestionStableId(
            @Bind("guid") String answerGuid,
            @Bind("operatorId") long operatorId,
            @Bind("instanceId") long instanceId,
            @Bind("questionStableId") String questionStableId,
            @Bind("createdAt") long createdAtMillis,
            @Bind("updatedAt") long updatedAtMillis);

    @SqlUpdate("insert into agreement_answer (answer_id, answer) values (:answerId, :value)")
    int insertAgreementValue(@Bind("answerId") long answerId, @Bind("value") boolean value);

    @SqlUpdate("insert into boolean_answer (answer_id, answer) values (:answerId, :value)")
    int insertBoolValue(@Bind("answerId") long answerId, @Bind("value") boolean value);

    @SqlUpdate("insert into date_answer (answer_id, year, month, day) values (:answerId, :year, :month, :day)")
    int insertDateValue(long answerId, Integer year, Integer month, Integer day);

    default int insertDateValue(long answerId, DateValue value) {
        if (value == null) {
            value = new DateValue(null, null, null);
        }
        return insertDateValue(answerId, value.getYear(), value.getMonth(), value.getDay());
    }

    @SqlUpdate("insert into numeric_answer (answer_id, int_value) values (:answerId, :value)")
    int insertNumericIntValue(@Bind("answerId") long answerId, @Bind("value") Long value);

    @SqlUpdate("insert into text_answer (answer_id, answer) values (:answerId, :value)")
    int insertTextValue(@Bind("answerId") long answerId, @Bind("value") String value);
}
