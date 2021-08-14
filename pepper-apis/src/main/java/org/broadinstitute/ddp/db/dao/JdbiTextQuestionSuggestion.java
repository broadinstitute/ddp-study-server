package org.broadinstitute.ddp.db.dao;

import java.util.Iterator;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiTextQuestionSuggestion extends SqlObject {
    @SqlBatch("insert into text_question_suggestion (text_question_id, suggestion, display_order)"
            + " values (:questionId, :suggestion, :displayOrder)")
    int[] insert(@Bind("questionId") long questionId,
               @Bind("suggestion") List<String> suggestions,
               @Bind("displayOrder") Iterator<Integer> displayOrder);

}
