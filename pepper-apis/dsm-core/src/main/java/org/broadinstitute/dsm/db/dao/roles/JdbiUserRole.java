package org.broadinstitute.dsm.db.dao.roles;

import java.util.Collection;
import java.util.List;

import org.broadinstitute.dsm.model.NameValue;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

public interface JdbiUserRole extends SqlObject {

    @SqlQuery ("select guid " +
            "from umbrella u " +
            "left join role r on (r.umbrella_id = u.umbrella_id) " +
            "left join user_role ur on (ur.role_id = r.role_id) " +
            "left join umbrella_study us on (us.umbrella_id = u.umbrella_id) " +
            "where ur.user_id = :userId;")
    Collection<String> getListOfAllowedRealmsGuids(@Bind ("userId") long userId);

    @SqlQuery ("select guid as name, guid as value " +
            "from  umbrella u " +
            "left join  role r on (r.umbrella_id = u.umbrella_id) " +
            "left join  user_role ur on (ur.role_id = r.role_id) " +
            "left join umbrella_study us on (us.umbrella_id = u.umbrella_id) " +
            "where ur.user_id = :userId")
    @RegisterConstructorMapper (NameValue.class)
    List<NameValue> getAllowedStudies(@Bind ("userId") long userId);

    @SqlQuery ("select p.name " +
            "from permissions p " +
            "left join role_permissions rp on (rp.permissions_id = p.permissions_id) " +
            "left join user_role  ur on (ur.role_id = rp.role_id) " +
            "where ur.user_id = :userId;")
    List<String> getPermissionsForUser(@Bind ("userId") long userId);

    @SqlQuery ("select p.name " +
            "from permissions p " +
            "left join role_permissions rp on (rp.permissions_id = p.permissions_id) " +
            "left join user_role  ur on (ur.role_id = rp.role_id) " +
            "left join user_profile  up on (ur.user_id = up.user_id) " +
            "where up.email = :email;")
    List<String> getPermissionsForUserEmail(@Bind ("email") String email);

    @SqlQuery ("select p.name " +
            "from permissions p " +
            "left join role_permissions rp on (rp.permissions_id = p.permissions_id) " +
            "left join user_role  ur on (ur.role_id = rp.role_id) " +
            "left join role r on (r.role_id = rp.role_id) " +
            "left join umbrella u on (r.umbrella_id = u.umbrella_id) " +
            "left join umbrella_study us on (us.umbrella_id = u.umbrella_id) " +
            "where ur.user_id = :userId and us.guid=:guid;")
    List<String> getUserRolesPerRealm(@Bind ("userId") long userId, @Bind ("guid") String study_guid);

}
