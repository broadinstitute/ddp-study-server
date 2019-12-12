package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.model.activity.types.PicklistRenderMode;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

public interface JdbiPicklistRenderMode extends SqlObject {

    @SqlQuery("select picklist_render_mode_id from picklist_render_mode where picklist_render_mode_code = :renderMode")
    long getModeId(PicklistRenderMode renderMode);
}
