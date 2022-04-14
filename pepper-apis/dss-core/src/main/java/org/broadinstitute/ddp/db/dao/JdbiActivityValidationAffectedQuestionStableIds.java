package org.broadinstitute.ddp.db.dao;

import java.util.List;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiActivityValidationAffectedQuestionStableIds extends SqlObject {
    default void _insertAffectedQuestionStableIdsForValidation(
            long activityValidationId, List<String> affectedQuestionStableIds, long umbrellaStudyId
    ) {
        affectedQuestionStableIds.forEach(
                field -> _insert(activityValidationId, field, umbrellaStudyId)
        );
    }

    @SqlUpdate(
            "INSERT INTO activity_validation_affected_question (activity_validation_id, question_id)"
            + " SELECT :activityValidationId, q.question_id FROM question AS q JOIN question_stable_code AS qsc"
            + " ON q.question_stable_code_id = qsc.question_stable_code_id"
            + " WHERE qsc.stable_id = :questionStableId AND qsc.umbrella_study_id = :umbrellaStudyId"
    )
    int _insert(
            @Bind("activityValidationId") long activityValidationId,
            @Bind("questionStableId") String questionStableId,
            @Bind("umbrellaStudyId") long umbrellaStudyId
    );
}
