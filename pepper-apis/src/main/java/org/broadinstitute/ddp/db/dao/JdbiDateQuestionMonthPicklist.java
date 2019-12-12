package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiDateQuestionMonthPicklist extends SqlObject {

    @SqlUpdate("insert into date_question_month_picklist"
            + " (date_question_id, use_month_names)"
            + " values (:dateQuestionId, :useMonthNames)")
    int insert(long dateQuestionId, boolean useMonthNames);
}
