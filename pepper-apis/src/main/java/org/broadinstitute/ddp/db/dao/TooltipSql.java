package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface TooltipSql extends SqlObject {

    @GetGeneratedKeys
    @SqlUpdate("insert into tooltip (text_template_id) values (:textTemplateId)")
    long insert(@Bind("textTemplateId") long textTemplateId);
}
