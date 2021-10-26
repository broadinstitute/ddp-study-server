package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlBatch;

import java.util.Iterator;
import java.util.List;

public interface JdbiDynamicSelectSourceQuestions extends SqlObject {
    @SqlBatch("insert into dynamic_select_source_questions (dynamic_question_id, dynamic_source_stable_id, display_order)"
            + " values (:questionId, :suggestion, :displayOrder)")
    int[] insert(@Bind("questionId") long questionId,
               @Bind("suggestion") List<String> suggestions,
               @Bind("displayOrder") Iterator<Integer> displayOrder);
}
