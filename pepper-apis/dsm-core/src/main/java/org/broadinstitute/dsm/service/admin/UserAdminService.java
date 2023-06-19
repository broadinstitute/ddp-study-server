package org.broadinstitute.dsm.service.admin;

import java.util.Optional;

import org.broadinstitute.dsm.db.dao.user.UserDao;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.route.admin.AddUserRoleRequest;

public class UserAdminService {

    private final String studyGroup;
    private final String operatorId;

    private static final String SQL_SELECT_ASSIGNEE =
            "SELECT user.user_id, user.name, user.email FROM access_user_role_group roleGroup, access_user user, access_role role, "
                    + "ddp_group, ddp_instance_group realmGroup, ddp_instance realm WHERE roleGroup.user_id = user.user_id AND roleGroup"
                    + ".role_id = role.role_id AND realm.ddp_instance_id = realmGroup.ddp_instance_id"
                    + " AND realmGroup.ddp_group_id = ddp_group.group_id AND ddp_group.group_id = roleGroup.group_id "
                    + "AND role.name = \"mr_request\" AND realm.instance_name = ?";

    private static final String SQL_SELECT_ROLES_FOR_INSTANCE =
            "SELECT role.name FROM ddp_instance ddp left join ddp_instance_role inRol on (inRol.ddp_instance_id = ddp.ddp_instance_id) "
                    + " left join instance_role role on (role.instance_role_id = inRol.instance_role_id) "
                    + "WHERE is_active = 1  and instance_name = ? ";

    private static final String SQL_SELECT_ROLE_FOR_STUDY_GROUP =
            "select ar.name from access_role ar"
                    + "join access_user_role_group aurg on aurg.role_id = ar.role_id"
                    + "join ddp_group dg on aurg.group_id = dg.group_id"
                    + "where dg.name = ? and ar.name = ?";

    public UserAdminService(String studyGroup, String operatorId) {
        this.studyGroup = studyGroup;
        this.operatorId = operatorId;
    }

    public void addUserToRole(AddUserRoleRequest req) {

        UserDao userDao = new UserDao();
        Optional<UserDto> res = userDao.getUserByEmail(req.getEmail());
        if (res.isEmpty()) {
            throw new DSMBadRequestException("Invalid user: " + req.getEmail());
        }

        UserDto userDto = res.get();
        // TODO: determine if operator can admin this user


        // assure role is for this study group

        userDto.
    }
}
