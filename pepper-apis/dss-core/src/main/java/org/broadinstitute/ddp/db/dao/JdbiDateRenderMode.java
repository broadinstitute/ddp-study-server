package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

public interface JdbiDateRenderMode extends SqlObject {

    @SqlQuery("select date_render_mode_id from date_render_mode where date_render_mode_code = :renderMode")
    long getModeId(@Bind("renderMode")DateRenderMode renderMode);
}
