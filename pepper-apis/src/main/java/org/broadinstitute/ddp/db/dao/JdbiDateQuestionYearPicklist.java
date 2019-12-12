package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiDateQuestionYearPicklist extends SqlObject {

    @SqlUpdate("insert into date_question_year_picklist"
            + " (date_question_id, years_forward, years_back, year_anchor, first_selected_year)"
            + " values (:dateQuestionId, :forward, :back, :anchor, :firstSelected)")
    int insert(long dateQuestionId, Integer forward, Integer back, Integer anchor, Integer firstSelected);
}
