package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.db.dto.RoleDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface RoleDao extends SqlObject {
    @UseStringTemplateSqlLocator
    @SqlUpdate("SELECT * FROM `role` WHERE id = :id")
    RoleDto findById(@Bind("id") Long id);
}
