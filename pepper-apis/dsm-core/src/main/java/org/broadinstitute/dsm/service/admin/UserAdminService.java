package org.broadinstitute.dsm.service.admin;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.broadinstitute.dsm.db.dao.user.UserDao;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.lddp.db.SimpleResult;
import spark.utils.StringUtils;

@Slf4j
public class UserAdminService {

    private final String operatorId;
    private final String studyGroup;

    private static final String USER_ADMIN_ROLE = "study_admin";

    private static final String SQL_SELECT_ROLES_FOR_USER =
            "SELECT ar.role_id, ar.name FROM access_role ar "
                    + "JOIN access_user_role_group aurg on aurg.role_id = ar.role_id "
                    + "WHERE aurg.user_id = ? AND aurg.group_id = ?";

    private static final String SQL_SELECT_ROLES_FOR_GROUP_AND_USER_ID =
            "SELECT ar.role_id, ar.name, dg.group_id FROM access_role ar "
                    + "JOIN access_user_role_group aurg on aurg.role_id = ar.role_id "
                    + "JOIN ddp_group dg on dg.group_id = aurg.group_id "
                    + "WHERE aurg.user_id = ? AND dg.name = ?";

    private static final String SQL_SELECT_USER_ROLE =
            "SELECT aurg.user_role_group_id FROM access_user_role_group aurg "
                    + "JOIN access_role ar on ar.role_id = aurg.role_id "
                    + "JOIN ddp_group dg on dg.group_id = aurg.group_id "
                    + "JOIN access_user au on au.user_id = aurg.user_id "
                    + "WHERE au.user_id = ? AND ar.role_id = ? AND dg.group_id = ?";

    private static final String SQL_SELECT_ROLE =
            "SELECT ar.role_id FROM access_role ar WHERE ar.name = ?";

    private static final String SQL_SELECT_GROUP =
            "SELECT dg.group_id FROM ddp_group dg WHERE dg.name = ?";

    private static final String SQL_SELECT_USER_BY_EMAIL =
            "SELECT au.user_id, au.name FROM access_user au WHERE au.email = ?";

    private static final String SQL_INSERT_USER_ROLE =
            "INSERT INTO access_user_role_group SET user_id = ?, role_id = ?, group_id = ?";

    private static final String SQL_DELETE_USER_ROLES =
            "DELETE FROM access_user_role_group WHERE user_id = ?";

    private static final String SQL_DELETE_USER_ROLE =
            "DELETE FROM access_user_role_group WHERE user_id = ? AND role_id = ? AND group_id = ?";

    private static final String SQL_INSERT_ROLE =
            "INSERT INTO access_role SET name = ?";

    private static final String SQL_INSERT_GROUP =
            "INSERT INTO ddp_group SET name = ?";

    private static final String SQL_DELETE_GROUP =
            "DELETE FROM ddp_group WHERE group_id = ?";

    private static final String SQL_INSERT_USER =
            "INSERT INTO ddp_group SET name = ?";

    public static final String SQL_SELECT_GROUP_FOR_REALM =
            "SELECT dg.group_id, dg.name from ddp_group dg "
                    + "JOIN ddp_instance_group dig on dig.ddp_group_id = dg.group_id "
                    + "JOIN ddp_instance di on di.ddp_instance_id = dig.ddp_instance_id "
                    + "WHERE di.instance_name = ?";

    public UserAdminService(String operatorId, String studyGroup) {
        this.operatorId = operatorId;
        this.studyGroup = studyGroup;
    }

    public void getStudyRoles() {

        int groupId = verifyOperatorAndGroup(studyGroup);
        throw new DsmInternalError("Not implemented");
    }

    public void addUserToRoles(UserRoleRequest req) {
        validateRoleRequest(req, true);

        int groupId = verifyOperatorAndGroup(studyGroup);

        for (String userEmail: req.getUsers()) {
            if (StringUtils.isBlank(userEmail)) {
                throw new DSMBadRequestException("Invalid user email: blank");
            }
            addUserRoles(userEmail, req.getRoles(), groupId, studyGroup);
        }
    }

    protected void addUserRoles(String email, List<String> roles, int groupId, String studyGroup) {
        int userId = getUserByEmail(email, groupId);

        for (String role : roles) {
            if (StringUtils.isBlank(role)) {
                throw new DsmInternalError("Invalid role: blank");
            }
            int roleId = verifyRole(role, groupId);
            try {
                addUserRole(userId, roleId, groupId);
                String msg = String.format("Set up role %s for user %s in study group %s", role, email, studyGroup);
                log.info(msg);
            } catch (Exception e) {
                String msg = String.format("Error adding user %s to role %s for study group %s", email, role, studyGroup);
                throw new DsmInternalError(msg, e);
            }
        }
    }

    public void removeUserFromRoles(UserRoleRequest req) {
        validateRoleRequest(req, true);

        int groupId = verifyOperatorAndGroup(studyGroup);

        for (String userEmail: req.getUsers()) {
            if (StringUtils.isBlank(userEmail)) {
                throw new DSMBadRequestException("Invalid user email: blank");
            }
            removeUserRoles(userEmail, req.getRoles(), groupId, studyGroup);
        }
    }

    protected void removeUserRoles(String email, List<String> roles, int groupId, String studyGroup) {
        // TODO loop is similar to addUserRoles -DC
        int userId = getUserByEmail(email, groupId);

        for (String role : roles) {
            if (StringUtils.isBlank(role)) {
                throw new DsmInternalError("Invalid role: blank");
            }
            int roleId = verifyRole(role, groupId);
            try {
                // ignore failure to remove
                deleteUserRole(userId, roleId, groupId);
                String msg = String.format("Removed role %s for user %s in study group %s", role, email, studyGroup);
                log.info(msg);
            } catch (Exception e) {
                String msg = String.format("Error removing user %s from role %s for study group %s", email, role, studyGroup);
                throw new DsmInternalError(msg, e);
            }
        }
    }

    public UserRoleResponse getUserRoles(UserRoleRequest req) {
        validateRoleRequest(req, false);

        int groupId = verifyOperatorAndGroup(studyGroup);

        UserRoleResponse userRoleResponse = new UserRoleResponse();
        for (String userEmail: req.getUsers()) {
            if (StringUtils.isBlank(userEmail)) {
                throw new DSMBadRequestException("Invalid user email: blank");
            }
            int userId = getUserByEmail(userEmail, groupId);

            userRoleResponse.addUserRoles(userEmail, getRolesForUser(userId, groupId));
        }
        return userRoleResponse;
    }

    public static String getStudyGroup(Map<String, String[]> queryParams) {
        String studyGroup = null;
        String realmGroup = null;
        if (queryParams.containsKey(RoutePath.STUDY_GROUP)) {
            studyGroup = queryParams.get(RoutePath.STUDY_GROUP)[0];
        }
        if (queryParams.containsKey(RoutePath.REALM)) {
            realmGroup = UserAdminService.getStudyGroupForRealm(queryParams.get(RoutePath.REALM)[0]);
        }
        if (realmGroup == null) {
            if (studyGroup == null) {
                throw new DSMBadRequestException("Invalid realm or study group");
            }
            return studyGroup;
        } else {
            if (studyGroup != null && !studyGroup.equals(realmGroup)) {
                String msg = String.format("Provided study group %s does not match provided realm %s", studyGroup,
                        queryParams.get(RoutePath.REALM)[0]);
                throw new DSMBadRequestException(msg);
            }
        }
        return realmGroup;
    }

    protected static String getStudyGroupForRealm(String realm) {
        NameAndId res = _getStudyGroupForRealm(realm);
        if (res == null) {
            throw new DSMBadRequestException("Invalid realm: " + realm);
        }
        return res.name;
    }

    private static NameAndId _getStudyGroupForRealm(String realm) {
        SimpleResult res = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_GROUP_FOR_REALM)) {
                stmt.setString(1, realm);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = new NameAndId(rs.getString(2), rs.getInt(1));
                    }
                }
            } catch (SQLException e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });

        if (res.resultException != null) {
            throw new DsmInternalError("Error getting roles groups for user", res.resultException);
        }
        return (NameAndId) res.resultValue;
    }

    protected void validateRoleRequest(UserRoleRequest req, boolean includesRoles) {
        if (StringUtils.isBlank(studyGroup)) {
            throw new DSMBadRequestException("Invalid study group: blank");
        }
        if (CollectionUtils.isEmpty(req.getUsers())) {
            throw new DSMBadRequestException("Invalid users: empty");
        }
        if (CollectionUtils.isEmpty(req.getRoles())) {
            if (includesRoles) {
                throw new DSMBadRequestException("Invalid roles: empty");
            }
        } else if (!includesRoles) {
            throw new DSMBadRequestException("Invalid roles: should be empty");
        }
    }

    public void createUser(UserRequest req) {
        if (StringUtils.isBlank(req.getName())) {
            throw new DSMBadRequestException("Invalid user name: blank");
        }
        if (StringUtils.isBlank(req.getEmail())) {
            throw new DSMBadRequestException("Invalid user email: blank");
        }
        // TODO: Currently we do not assign users to study groups, but when we do validate UserRequest.studyGroup -DC

        UserDao userDao = new UserDao();
        userDao.create(req.asUserDto());
    }

    public void removeUser(UserRequest req) {
        if (StringUtils.isBlank(req.getEmail())) {
            throw new DSMBadRequestException("Invalid user email: blank");
        }

        int userId = getUserByEmail(req.getEmail(), -1);
        deleteUserRoles(userId);
        UserDao userDao = new UserDao();
        userDao.delete(userId);
    }

    protected int verifyOperatorAndGroup(String studyGroup) {
        int adminId;
        try {
            adminId = Integer.parseInt(operatorId);
        } catch (NumberFormatException e) {
            throw new DSMBadRequestException("Invalid operator ID format: " + operatorId);
        }
        return verifyOperatorForGroup(adminId, studyGroup);
    }

    public void addStudyRole(StudyRoleRequest req) {
        String group = studyGroup;
        if (StringUtils.isBlank(group)) {
            throw new DSMBadRequestException("Invalid study group: blank");
        }
        String role = req.getRole();
        if (StringUtils.isBlank(role)) {
            throw new DSMBadRequestException("Invalid role: blank");
        }

        int adminId;
        try {
            adminId = Integer.parseInt(operatorId);
        } catch (NumberFormatException e) {
            throw new DSMBadRequestException("Invalid operator ID format: " + operatorId);
        }
        int groupId = verifyOperatorForGroup(adminId, group);

        try {
            addRole(role, groupId);
        } catch (Exception e) {
            String msg = String.format("Error adding role %s to study group %s", role, group);
            throw new DsmInternalError(msg, e);
        }
    }

    protected static int verifyOperatorForGroup(int operatorId, String studyGroup) {

        List<NameAndId> roles = getRolesAndGroupForUser(operatorId, studyGroup);
        if (roles.isEmpty()) {
            String msg = String.format("No roles found for operator %s and group %s", operatorId, studyGroup);
            throw new DSMBadRequestException(msg);
        }
        List<String> roleNames = roles.stream().map(r -> r.name).collect(Collectors.toList());
        if (!roleNames.contains(USER_ADMIN_ROLE)) {
            throw new DSMBadRequestException("Operator does not have administrator privileges: " + operatorId);
        }
        return roles.get(0).id;
    }

    protected static List<String> getRolesForUser(int userId, int groupId) {
        SimpleResult res = inTransaction(conn -> {
            List<String> roles = new ArrayList<>();
            SimpleResult dbVals = new SimpleResult(roles);
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_ROLES_FOR_USER)) {
                stmt.setInt(1, userId);
                stmt.setInt(2, groupId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        roles.add(rs.getString(2));
                    }
                }
            } catch (SQLException e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });

        if (res.resultException != null) {
            throw new DsmInternalError("Error getting roles groups for user", res.resultException);
        }
        return (List<String>) res.resultValue;
    }

    protected static List<NameAndId> getRolesAndGroupForUser(int userId, String studyGroup) {
        SimpleResult res = inTransaction(conn -> {
            List<NameAndId> roles = new ArrayList<>();
            SimpleResult dbVals = new SimpleResult(roles);
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_ROLES_FOR_GROUP_AND_USER_ID)) {
                stmt.setInt(1, userId);
                stmt.setString(2, studyGroup);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        roles.add(new NameAndId(rs.getString(2), rs.getInt(3)));
                    }
                }
            } catch (SQLException e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });

        if (res.resultException != null) {
            throw new DsmInternalError("Error getting study groups for user", res.resultException);
        }
        return (List<NameAndId>) res.resultValue;
    }

    protected static int verifyRole(String role, int groupId) {
        // TODO: Currently we do not track the valid roles for a group, but verify with
        // groupId once we do -DC
        SimpleResult res = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_ROLE)) {
                stmt.setString(1, role);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = rs.getInt(1);
                    }
                }
            } catch (SQLException e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });

        if (res.resultException != null) {
            String msg = String.format("Error getting role %s for study group, ID: %d", role, groupId);
            throw new DsmInternalError(msg, res.resultException);
        }

        if (res.resultValue == null) {
            throw new DSMBadRequestException("Invalid role for study group: " + role);
        }
        return (int) res.resultValue;
    }

    protected static int verifyStudyGroup(String studyGroup) {
        SimpleResult res = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_GROUP)) {
                stmt.setString(1, studyGroup);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = rs.getInt(1);
                    }
                }
            } catch (SQLException e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });

        if (res.resultException != null) {
            String msg = String.format("Error getting study group %s", studyGroup);
            throw new DsmInternalError(msg, res.resultException);
        }

        if (res.resultValue == null) {
            throw new DSMBadRequestException("Invalid study group: " + studyGroup);
        }
        return (int) res.resultValue;
    }

    protected static int getUserByEmail(String email, int groupId) {
        // TODO: Currently we do not track the valid roles for a group, but get by
        // groupId once we do -DC
        SimpleResult res = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_USER_BY_EMAIL)) {
                stmt.setString(1, email);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = rs.getInt(1);
                    }
                }
            } catch (SQLException e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });

        if (res.resultException != null) {
            String msg = String.format("Error getting user %s for study group, ID: %d", email, groupId);
            throw new DsmInternalError(msg, res.resultException);
        }

        if (res.resultValue == null) {
            throw new DSMBadRequestException("Invalid user for study group: " + email);
        }
        return (int) res.resultValue;
        // TODO: see if access_user.is_active is used -DC
    }

    protected static int getUserRole(int userId, int roleId, int groupId) {
        SimpleResult res = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_USER_ROLE)) {
                stmt.setInt(1, userId);
                stmt.setInt(2, roleId);
                stmt.setInt(3, groupId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = rs.getInt(1);
                    }
                }
            } catch (SQLException e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });

        if (res.resultException != null) {
            String msg = String.format("Error getting user role for study group: userId=%d, roleId=%d, groupO=Id=%d",
                    userId, roleId, groupId);
            throw new DsmInternalError(msg, res.resultException);
        }

        if (res.resultValue == null) {
            return -1;
        }
        return (int) res.resultValue;
    }

    protected static int addUserRole(int userId, int roleId, int groupId) throws Exception {
        // since we do not have a key constraint for duplicate entries we need to check first
        int userRoleId = getUserRole(userId, roleId, groupId);
        if (userRoleId != -1) {
            return userRoleId;
        }
        SimpleResult res = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_USER_ROLE, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1, userId);
                stmt.setInt(2, roleId);
                stmt.setInt(3, groupId);
                int result = stmt.executeUpdate();
                if (result != 1) {
                    dbVals.resultException = new DsmInternalError("Error adding user to role. Result count was " + result);
                }
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        dbVals.resultValue = rs.getInt(1);
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (res.resultException != null) {
            throw res.resultException;
        }
        return (int) res.resultValue;
    }

    protected static int addRole(String role, int groupId) throws Exception {
        // TODO: Currently we do not track the valid roles for a group, but enroll with
        // groupId once we do -DC
        SimpleResult res = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_ROLE, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, role);
                int result = stmt.executeUpdate();
                if (result != 1) {
                    dbVals.resultException = new DsmInternalError("Result count was " + result);
                }
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        dbVals.resultValue = rs.getInt(1);
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (res.resultException != null) {
            throw new DsmInternalError("Error adding role " + role, res.resultException);
        }
        return (int) res.resultValue;
    }

    protected static int addStudyGroup(String groupName) {
        SimpleResult res = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_GROUP, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, groupName);
                int result = stmt.executeUpdate();
                if (result != 1) {
                    dbVals.resultException = new DsmInternalError("Result count was " + result);
                }
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        dbVals.resultValue = rs.getInt(1);
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (res.resultException != null) {
            throw new DsmInternalError("Error adding study group " + groupName, res.resultException);
        }
        return (int) res.resultValue;
    }

    protected static int deleteUserRole(int userId, int roleId, int groupId) {
        return inTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(SQL_DELETE_USER_ROLE)) {
                stmt.setInt(1, userId);
                stmt.setInt(2, roleId);
                stmt.setInt(3, groupId);
                return stmt.executeUpdate();
            } catch (SQLException ex) {
                String msg = String.format("Error deleting user role: userId=%d, roleId=%d, groupId=%d",
                        userId, roleId, groupId);
                throw new DsmInternalError(msg, ex);
            }
        });
    }

    protected static int deleteUserRoles(int userId) {
        return inTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(SQL_DELETE_USER_ROLES)) {
                stmt.setInt(1, userId);
                return stmt.executeUpdate();
            } catch (SQLException ex) {
                String msg = String.format("Error deleting user roles: userId=%d", userId);
                throw new DsmInternalError(msg, ex);
            }
        });
    }

    protected static int deleteStudyGroup(int groupId) {
        return inTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(SQL_DELETE_GROUP)) {
                stmt.setInt(1, groupId);
                return stmt.executeUpdate();
            } catch (SQLException ex) {
                String msg = String.format("Error deleting study group: groupId=%d", groupId);
                throw new DsmInternalError(msg, ex);
            }
        });
    }

    protected static class NameAndId {
        public final String name;
        public final int id;

        public NameAndId(String name, int id) {
            this.name = name;
            this.id = id;
        }
    }
}
