package org.broadinstitute.dsm.db.dao.roles;

import java.util.Collection;
import java.util.List;

import org.broadinstitute.dsm.db.dto.user.AssigneeDto;
import org.broadinstitute.dsm.model.NameValue;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

public interface JdbiUserRole extends SqlObject {

    @SqlQuery ("select guid " +
            "from umbrella u " +
            "left join role r on (r.umbrella_id = u.umbrella_id) " +
            "left join user_role ur on (ur.role_id = r.role_id) " +
            "left join umbrella_study us on (us.umbrella_id = u.umbrella_id) " +
            "where ur.user_id = :userId;")
    Collection<String> getListOfAllowedRealmsGuids(@Bind ("userId") long userId);

    @SqlQuery ("SELECT DISTINCT realm.instance_name" +
            " from ddp_instance realm where realm.study_guid in (<studies>)")
    Collection<String> getAllowedStudiesNames(@BindList (value = "studies", onEmpty = BindList.EmptyHandling.NULL) List<String> studies);

    @SqlQuery ("SELECT DISTINCT realm.instance_name as name, realm.display_name as value" +
            " from ddp_instance realm where realm.study_guid in (<studies>)")
    @RegisterConstructorMapper (NameValue.class)
    List<NameValue> getAllowedStudiesNameVale(@BindList (value = "studies", onEmpty = BindList.EmptyHandling.NULL) List<String> studies);

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
    List<String> getUserRolesPerRealm(@Bind ("userId") long userId, @Bind ("guid") String studyGuid);

    @SqlQuery (" select up.user_id as assigneeId, concat(up.first_name, \" \", up.last_name) as name, email " +
            "    from user_profile up " +
            "    left join user_role ur on (ur.user_id = up.user_id) " +
            "    left join role r on (r.role_id = ur.role_id) " +
            "    left join umbrella u on (r.umbrella_id = u.umbrella_id) " +
            "    left join role_permissions rp on (rp.role_id = ur.role_id) " +
            "    left join permissions p on (p.permissions_id = rp.permissions_id) " +
            "    left join umbrella_study us on (us.umbrella_id = u.umbrella_id) " +
            "    where p.name = \"mr_request\" and us.guid = :guid;")
    @RegisterConstructorMapper (AssigneeDto.class)
    List<AssigneeDto> getAssigneesForStudy(@Bind ("guid") String studyGuid);

}
