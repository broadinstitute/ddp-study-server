package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.model.activity.types.PicklistSelectMode;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

public interface JdbiPicklistSelectMode extends SqlObject {

    @SqlQuery("select picklist_select_mode_id from picklist_select_mode where picklist_select_mode_code = :selectMode")
    long getModeId(PicklistSelectMode selectMode);
}
