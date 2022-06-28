package org.broadinstitute.dsm.db.jdbi;

import java.util.List;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiRole extends SqlObject {

    @SqlQuery("SELECT r.role_id, r.name, r.description, r.umbrella_id FROM  role r  "
            + "left join  umbrella_study us on (r.umbrella_id = us.umbrella_id)  "
            + "where us.guid =:umbrellaGuid")
    @RegisterConstructorMapper(RoleDto.class)
    List<RoleDto> getAllRolesForStudy(@Bind("umbrellaGuid") String studyGuid);

    @SqlUpdate("update user_role  set role_id=:roleId where user_role_id in(   "
            + "select user_role_id from (select ur.user_role_id from  user_role ur    "
            + "left join role  r  "
            + "on (r.role_id = ur.role_id)   "
            + "left join umbrella_study  us  "
            + "on (r.umbrella_id = us.umbrella_id)"
            + "where us.guid =:studyGuid   "
            + " and ur.user_id = :userId   "
            + " ) as sth) and user_role_id <> 0")
    int updateRoleForUser(@Bind("userId") long userId, @Bind("roleId") long roleId, @Bind("studyGuid") String studyGuid);


}
