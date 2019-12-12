package org.broadinstitute.ddp.db.dao;

import java.util.Optional;

import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiBooleanAnswer extends SqlObject {

    @SqlUpdate("delete from boolean_answer where answer_id in (select answer_id from answer where "
            + "question_id = :questionId and activity_instance_id = :activityInstanceId)")
    int deleteAllAnswerAnswersForQuestion(long activityInstanceId, long questionId);

    @SqlUpdate("insert into boolean_answer(answer_id, answer) values (:answerId,:answer)")
    boolean insert(long answerId, boolean answer);

    @SqlQuery("select ba.answer"
            + " from answer as a"
            + " join boolean_answer as ba on ba.answer_id = a.answer_id"
            + " where a.answer_guid = :answerGuid")
    Optional<Boolean> findValueByGuid(@Bind("answerGuid") String answerGuid);

    @SqlQuery("select ba.answer as value, ba.answer_id, a.answer_guid, qsc.stable_id as questionStableId"
            + " from answer as a"
            + " join boolean_answer as ba on ba.answer_id = a.answer_id"
            + " join question as q on q.question_id = a.question_id"
            + " join question_stable_code as qsc on q.question_stable_code_id = qsc.question_stable_code_id"
            + " where a.answer_id = :answerId"
    )
    @RegisterConstructorMapper(BoolAnswer.class)
    BoolAnswer findByAnswerId(@Bind("answerId") long answerId);


    @SqlQuery("select ba.answer"
            + " from boolean_answer as ba"
            + " join answer as a on a.answer_id = ba.answer_id"
            + " join activity_instance as ai on  ai.activity_instance_id = a.activity_instance_id"
            + " where a.question_id = :questionId"
            + " and ai.activity_instance_guid = :activityInstanceGuid"
            + " order by a.last_updated_at limit 1")
    Optional<Boolean> findAnswerByQuestionIdActivityInstanceGuid(@Bind("questionId") long questionId,
                                                                 @Bind("activityInstanceGuid") String activityInstanceGuid);
}
