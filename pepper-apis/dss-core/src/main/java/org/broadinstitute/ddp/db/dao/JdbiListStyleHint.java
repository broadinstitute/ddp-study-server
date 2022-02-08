package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.model.activity.types.ListStyleHint;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

public interface JdbiListStyleHint extends SqlObject {

    @SqlQuery("select list_style_hint_id from list_style_hint where list_style_hint_code = :hint")
    long getHintId(ListStyleHint hint);
}
