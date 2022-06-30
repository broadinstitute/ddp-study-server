package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.db.dto.UserRoleDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface UserRoleDao extends SqlObject {
    @UseStringTemplateSqlLocator
    @SqlUpdate("INSERT INTO `user_role` "
             + "        SET role_id = :roleId, "
             + "            user_id = :userId")
    int insert(@BindBean final UserRoleDto centerUser);
}
