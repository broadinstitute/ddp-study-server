package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.db.dto.CopyAnswerEventActionDto;
import org.broadinstitute.ddp.model.event.CopyAnswerTarget;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiCopyAnswerEventAction extends SqlObject {
    @SqlUpdate("INSERT INTO copy_answer_event_action (event_action_id, copy_answer_target_id, source_question_stable_id)"
            + " VALUES (:actionId, (SELECT copy_answer_target_id FROM copy_answer_target WHERE copy_target=:answerTarget),"
            + ":questionStableCodeDbId)")
    int insert(@Bind("actionId") long eventActionId, @Bind("questionStableCodeDbId") long sourceQuestionStableId,
               @Bind("answerTarget") CopyAnswerTarget answerTarget);

    @SqlUpdate("delete from copy_answer_event_action where event_action_id = :actionId")
    int deleteById(@Bind("actionId") long eventActionId);

    @SqlQuery("SELECT ea.event_action_id id, event_action_type_id, message_destination_id, ct.copy_target copyTarget, qsc.stable_id "
            + "stableCode "
            + "FROM event_action ea "
            + "JOIN copy_answer_event_action cea ON ea.event_action_id = cea.event_action_id "
            + "JOIN copy_answer_target ct ON cea.copy_answer_target_id = ct.copy_answer_target_id "
            + "JOIN question_stable_code qsc ON qsc.question_stable_code_id=cea.source_question_stable_id  "
            + "WHERE ea.event_action_id=:eventId")
    @RegisterConstructorMapper(CopyAnswerEventActionDto.class)
    CopyAnswerEventActionDto findById(@Bind("eventId") long eventId);
}
