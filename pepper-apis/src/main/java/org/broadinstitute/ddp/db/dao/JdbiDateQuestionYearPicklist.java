package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiDateQuestionYearPicklist extends SqlObject {

    @SqlUpdate("insert into date_question_year_picklist"
            + " (date_question_id, years_forward, years_back, year_anchor, first_selected_year, allow_future_years)"
            + " values (:dateQuestionId, :forward, :back, :anchor, :firstSelected, :allowFutureYears)")
    int insert(
            @Bind("dateQuestionId") long dateQuestionId,
            @Bind("forward") Integer forward,
            @Bind("back") Integer back,
            @Bind("anchor") Integer anchor,
            @Bind("firstSelected") Integer firstSelected,
            @Bind("allowFutureYears") boolean allowFutureYears);
}
