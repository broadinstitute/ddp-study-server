package org.broadinstitute.ddp.db.dao;

import java.util.Optional;

import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiTextAnswer extends SqlObject {

    @SqlUpdate("update text_answer set answer = :value where answer_id = "
            + "(select answer_id from answer where answer_guid = :answerGuid)")
    int updateByGuid(String answerGuid, String value);

    @SqlUpdate("insert into text_answer(answer_id, answer) values (:answerId, :answer)")
    int insert(@Bind("answerId") long answerId, @Bind("answer") String answer);

    @SqlQuery("select ta.answer"
            + " from text_answer as ta"
            + " join answer as a on a.answer_id = ta.answer_id"
            + " join activity_instance as ai on  ai.activity_instance_id = a.activity_instance_id"
            + " where a.question_id = :questionId"
            + " and ai.activity_instance_guid = :activityInstanceGuid"
            + " order by a.last_updated_at limit 1")
    Optional<String> findAnswerByQuestionIdActivityInstanceGuid(@Bind("questionId") long questionId,
                                                                 @Bind("activityInstanceGuid") String activityInstanceGuid);

    @SqlQuery("select ta.answer as value, ta.answer_id, a.answer_guid, qsc.stable_id as questionStableId"
            + " from answer as a"
            + " join text_answer as ta on ta.answer_id = a.answer_id"
            + " join question as q on q.question_id = a.question_id"
            + " join question_stable_code as qsc on q.question_stable_code_id = qsc.question_stable_code_id"
            + " where a.answer_id = :answerId"
    )
    @RegisterConstructorMapper(TextAnswer.class)
    TextAnswer findByAnswerId(@Bind("answerId") long answerId);

    @SqlUpdate("delete from text_answer where answer_id in (select answer_id from answer where "
            + "question_id = :questionId and activity_instance_id = :activityInstanceId)")
    int deleteAllAnswerValuesForQuestion(long activityInstanceId, long questionId);
}
