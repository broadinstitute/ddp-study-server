package org.broadinstitute.ddp.db.dao;

import java.util.List;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiTextQuestionSuggestion extends SqlObject {

    @SqlUpdate("insert into text_question_suggestion (text_question_id, suggestion, display_order)"
            + " values (:questionId, :suggestion, :displayOrder)"
    )
    int insert(@Bind("questionId") long questionId,
               @Bind("suggestion") String suggestion,
               @Bind("displayOrder") int displayOrder);

    @SqlQuery("select suggestion"
            + " from text_question_suggestion "
            + " where text_question_id = :questionId"
            + " order by display_order, suggestion "
    )
    List<String> getTextQuestionSuggestions(@Bind("questionId") long questionId);

}
