package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.db.dto.CenterUserDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;


public interface CenterUserDao extends SqlObject {
    @UseStringTemplateSqlLocator
    @SqlUpdate("INSERT INTO `center_user` "
             + "        SET center_id = :centerId, "
             + "              user_id = :userId")
    int insert(@BindBean final CenterUserDto centerUser);
}
