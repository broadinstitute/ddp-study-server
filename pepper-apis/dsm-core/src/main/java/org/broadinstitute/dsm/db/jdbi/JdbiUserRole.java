package org.broadinstitute.dsm.db.jdbi;

import java.util.Collection;
import java.util.List;

import org.broadinstitute.dsm.db.dto.user.AssigneeDto;
import org.broadinstitute.dsm.db.dto.user.UserRoleDto;
import org.broadinstitute.dsm.model.NameValue;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiUserRole extends SqlObject {

    @SqlQuery("select us.guid "
            + "from umbrella u "
            + "left join role r on (r.umbrella_id = u.umbrella_id) "
            + "left join user_role ur on (ur.role_id = r.role_id) "
            + "left join user ut on (ur.user_id = ut.user_id) "
            + "left join umbrella_study us on (us.umbrella_id = u.umbrella_id) "
            + "where ur.user_id = :userId and ut.is_active = 1")
    Collection<String> getListOfAllowedRealmsGuids(@Bind("userId") long userId);

    @SqlQuery("SELECT DISTINCT realm.instance_name"
            + " from ddp_instance realm where realm.study_guid in (<studies>)")
    Collection<String> getAllowedStudiesNames(@BindList(value = "studies", onEmpty = BindList.EmptyHandling.NULL) List<String> studies);

    @SqlQuery("SELECT DISTINCT realm.instance_name as name, realm.display_name as value"
            + " from ddp_instance realm where realm.study_guid in (<studies>)")
    @RegisterConstructorMapper(NameValue.class)
    List<NameValue> getAllowedStudiesNameVale(@BindList(value = "studies", onEmpty = BindList.EmptyHandling.NULL) List<String> studies);

    @SqlQuery("select p.name "
            + "from permissions p "
            + "left join role_permissions rp on (rp.permissions_id = p.permissions_id) "
            + "left join user_role  ur on (ur.role_id = rp.role_id) "
            + "left join user u on (ur.user_id = u.user_id) "
            + "where ur.user_id = :userId and u.is_active = 1")
    List<String> getPermissionsForUser(@Bind("userId") long userId);

    @SqlQuery("select p.name "
            + "from permissions p "
            + "left join role_permissions rp on (rp.permissions_id = p.permissions_id) "
            + "left join user_role  ur on (ur.role_id = rp.role_id) "
            + "left join user_profile  up on (ur.user_id = up.user_id) "
            + "where up.email = :email;")
    List<String> getPermissionsForUserEmail(@Bind("email") String email);

    @SqlQuery("select p.name "
            + "from permissions p "
            + "left join role_permissions rp on (rp.permissions_id = p.permissions_id) "
            + "left join user_role  ur on (ur.role_id = rp.role_id) "
            + "left join user ut on (ur.user_id = ut.user_id) "
            + "left join role r on (r.role_id = rp.role_id) "
            + "left join umbrella u on (r.umbrella_id = u.umbrella_id) "
            + "left join umbrella_study us on (us.umbrella_id = u.umbrella_id) "
            + "where ur.user_id = :userId and us.guid=:guid and ut.is_active = 1")
    List<String> getUserRolesPerRealm(@Bind("userId") long userId, @Bind("guid") String studyGuid);

    @SqlQuery(" select up.user_id as assigneeId, concat(up.first_name, \" \", up.last_name) as name, email "
            + "    from user_profile up "
            + "    left join user_role ur on (ur.user_id = up.user_id) "
            + "    left join role r on (r.role_id = ur.role_id) "
            + "    left join umbrella u on (r.umbrella_id = u.umbrella_id) "
            + "    left join role_permissions rp on (rp.role_id = ur.role_id) "
            + "    left join permissions p on (p.permissions_id = rp.permissions_id) "
            + "    left join umbrella_study us on (us.umbrella_id = u.umbrella_id) "
            + "    where p.name = \"mr_request\" and us.guid = :guid;")
    @RegisterConstructorMapper(AssigneeDto.class)
    List<AssigneeDto> getAssigneesForStudy(@Bind("guid") String studyGuid);

    @SqlQuery("SELECT  name from access_role")
    List<String> getAllRolesFromOldSchema();

    @SqlBatch("INSERT INTO permissions (name) VALUES ( :roles )")
    void insertOldRolesIntoPermissionTable(@Bind("roles") List<String> roles);

    @SqlUpdate("INSERT INTO user_role (user_id, role_id) SELECT :userId, role_id from role where name=:roleName and "
            + "umbrella_id  in (select umbrella_id from umbrella_Study where guid  = :umbrellaGuid )")
    int insertNewUserRole(@Bind("userId") long userId, @Bind("roleName") String roleName, @Bind("umbrellaGuid") String umbrellaGuid);

    @SqlQuery(
            "SELECT u.guid, u.user_id, up.first_name, up.last_name, concat(up.first_name, \" \", up.last_name) as name, up.email, up.phone as phoneNumber,"
                    + " u.auth0_user_id, u.hruid as shortId, u.is_active, r.name as roleName, r.role_id, r.description, r.umbrella_id "
                    + "FROM user u "
                    + "left join user_profile up on (u.user_id = up.user_id) "
                    + "left join user_role ur on (u.user_id = ur.user_id) "
                    + "left join role r on (r.role_id = ur.role_id) "
                    + "left join umbrella_study us on (us.umbrella_id = r.umbrella_id) "
                    + "where us.guid  = :umbrellaGuid and u.is_active = 1")
    @RegisterConstructorMapper(UserRoleDto.class)
    List<UserRoleDto> getAllUsersWithRoleInRealm(@Bind("umbrellaGuid") String studyGuid);
}
