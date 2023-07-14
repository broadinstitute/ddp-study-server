package org.broadinstitute.dsm.service.admin;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    protected static final String USER_ADMIN_ROLE = "study_user_admin";
    protected static final String PEPPER_ADMIN_ROLE = "pepper_admin";

    private static final String SQL_SELECT_STUDY_GROUP =
            "SELECT dg.group_id FROM ddp_group dg WHERE dg.name = ?";

    private static final String SQL_SELECT_STUDY_ROLES =
            "SELECT ar.role_id, ar.name FROM access_role ar "
                    + "JOIN ddp_group_role dgr on ar.role_id = dgr.role_id "
                    + "WHERE dgr.group_id = ?";

    private static final String SQL_SELECT_STUDY_ROLES_FOR_ADMIN =
            "SELECT ar.role_id, ar.name FROM access_role ar "
                    + "JOIN ddp_group_role dgr on ar.role_id = dgr.role_id "
                    + "WHERE dgr.group_id = ? "
                    + "AND dgr.admin_role_id = (select role_id from access_role ar where ar.name = ?)";

    private static final String SQL_SELECT_ROLES_FOR_USER =
            "SELECT ar.role_id, ar.name FROM access_role ar "
                    + "JOIN access_user_role_group aurg on aurg.role_id = ar.role_id "
                    + "WHERE aurg.user_id = ? AND aurg.group_id = ?";

    private static final String SQL_SELECT_ROLES_FOR_STUDY_USERS =
            "SELECT aurg.user_id, ar.name FROM access_role ar "
                    + "JOIN access_user_role_group aurg on aurg.role_id = ar.role_id "
                    + "WHERE aurg.group_id = ?";

    private static final String SQL_SELECT_ROLES_FOR_GROUP_AND_USER =
            "SELECT ar.role_id, ar.name, dg.group_id FROM access_role ar "
                    + "JOIN access_user_role_group aurg on aurg.role_id = ar.role_id "
                    + "JOIN ddp_group dg on dg.group_id = aurg.group_id "
                    + "WHERE aurg.user_id = ? AND dg.group_id = ?";

    private static final String SQL_SELECT_USER_ROLE =
            "SELECT aurg.user_role_group_id FROM access_user_role_group aurg "
                    + "JOIN access_role ar on ar.role_id = aurg.role_id "
                    + "JOIN ddp_group dg on dg.group_id = aurg.group_id "
                    + "JOIN access_user au on au.user_id = aurg.user_id "
                    + "WHERE au.user_id = ? AND ar.role_id = ? AND dg.group_id = ?";

    private static final String SQL_SELECT_ROLE =
            "SELECT ar.role_id FROM access_role ar WHERE ar.name = ?";

    private static final String SQL_SELECT_ROLE_FOR_ADMIN =
            "SELECT ar.role_id FROM access_role ar WHERE ar.name = ?";

    private static final String SQL_SELECT_USER_BY_EMAIL =
            "SELECT au.user_id, au.name FROM access_user au WHERE au.email = ?";

    private static final String SQL_SELECT_USERS_FOR_GROUP =
            "SELECT au.user_id, au.name, au.email, au.phone_number FROM access_user au "
                    + "JOIN access_user_role_group aurg on aurg.user_id = au.user_id "
                    + "JOIN ddp_group dg on dg.group_id = aurg.group_id "
                    + "WHERE dg.group_id = ? AND au.is_active = 1";

    private static final String SQL_INSERT_USER_ROLE =
            "INSERT INTO access_user_role_group SET user_id = ?, role_id = ?, group_id = ?";

    private static final String SQL_DELETE_USER_ROLES =
            "DELETE FROM access_user_role_group WHERE user_id = ?";

    private static final String SQL_DELETE_GROUP_ROLE =
            "DELETE FROM ddp_group_role WHERE group_role_id = ?";

    private static final String SQL_DELETE_USER_ROLE =
            "DELETE FROM access_user_role_group WHERE user_id = ? AND role_id = ? AND group_id = ?";

    private static final String SQL_INSERT_ROLE =
            "INSERT INTO access_role SET name = ?";

    private static final String SQL_INSERT_GROUP =
            "INSERT INTO ddp_group SET name = ?";

    private static final String SQL_DELETE_GROUP =
            "DELETE FROM ddp_group WHERE group_id = ?";

    private static final String SQL_INSERT_GROUP_ROLE =
            "INSERT INTO ddp_group_role SET group_id = ?, role_id = ?, admin_role_id = ?";

    public static final String SQL_SELECT_GROUP_FOR_REALM =
            "SELECT dg.group_id, dg.name from ddp_group dg "
                    + "JOIN ddp_instance_group dig on dig.ddp_group_id = dg.group_id "
                    + "JOIN ddp_instance di on di.ddp_instance_id = dig.ddp_instance_id "
                    + "WHERE di.instance_name = ?";

    public UserAdminService(String operatorId, String studyGroup) {
        this.operatorId = operatorId;
        this.studyGroup = studyGroup;
    }

    public void addUserToRoles(UserRoleRequest req) {
        validateRoleRequest(req, true);

        int groupId = verifyStudyGroup(studyGroup);
        Map<String, RoleInfo> studyRoles = getAdminRoles(groupId);
        List<String> roleNames = req.getRoles();
        validateRoles(roleNames, studyRoles.keySet());

        for (String userEmail: req.getUsers()) {
            if (StringUtils.isBlank(userEmail)) {
                throw new DSMBadRequestException("Invalid user email: blank");
            }
            addUserRoles(userEmail, roleNames, groupId, studyRoles);
        }
    }

    protected void addUserRoles(String email, List<String> roles, int groupId, Map<String, RoleInfo> studyRoles) {
        int userId = getUserByEmail(email, groupId);

        for (String role : roles) {
            if (StringUtils.isBlank(role)) {
                throw new DsmInternalError("Invalid role: blank");
            }
            // role names already validated
            int roleId = studyRoles.get(role).roleId;
            try {
                addUserRole(userId, roleId, groupId);
                String msg = String.format("Added role %s for user %s in study group %s", role, email, studyGroup);
                log.info(msg);
            } catch (Exception e) {
                String msg = String.format("Error adding user %s to role %s for study group %s", email, role, studyGroup);
                throw new DsmInternalError(msg, e);
            }
        }
    }

    public void removeUserFromRoles(UserRoleRequest req) {
        validateRoleRequest(req, true);

        int groupId = verifyStudyGroup(studyGroup);
        Map<String, RoleInfo> studyRoles = getAdminRoles(groupId);
        List<String> roleNames = req.getRoles();
        validateRoles(roleNames, studyRoles.keySet());

        for (String userEmail: req.getUsers()) {
            if (StringUtils.isBlank(userEmail)) {
                throw new DSMBadRequestException("Invalid user email: blank");
            }
            removeUserRoles(userEmail, roleNames, groupId, studyRoles);
        }
    }

    protected void removeUserRoles(String email, List<String> roles, int groupId, Map<String, RoleInfo> studyRoles) {
        // TODO loop is similar to addUserRoles -DC
        int userId = getUserByEmail(email, groupId);

        for (String role : roles) {
            if (StringUtils.isBlank(role)) {
                throw new DsmInternalError("Invalid role: blank");
            }
            // role names already validated
            int roleId = studyRoles.get(role).roleId;
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

    protected void validateRoleRequest(UserRoleRequest req, boolean includesRoles) {
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

    protected void validateRoles(List<String> roleNames, Set<String> validRoleNames) {
        if (validRoleNames.containsAll(roleNames)) {
            return;
        }
        Collection<String> badRoles = CollectionUtils.subtract(validRoleNames, roleNames);
        String msg = String.format("Invalid roles for study group %s: %s", studyGroup, String.join(",", badRoles));
        throw new DSMBadRequestException(msg);
    }

    public void createUser(AddUserRequest req) {
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

    public void removeUser(AddUserRequest req) {
        if (StringUtils.isBlank(req.getEmail())) {
            throw new DSMBadRequestException("Invalid user email: blank");
        }

        int userId = getUserByEmail(req.getEmail(), -1);
        deleteUserRoles(userId);
        UserDao userDao = new UserDao();
        userDao.delete(userId);
    }

    public UserRoleResponse getUserRoles(UserRequest req) {
        int groupId = verifyStudyGroup(studyGroup);
        Map<String, RoleInfo> studyRoles = getAdminRoles(groupId);

        // get access_user info for the users
        Map<Integer, UserInfo> studyUsers = getStudyUsers(groupId, req);
        // if list of users provided, create a list of just those users
        // get roles for users (should include admin predictate)
        Map<Integer, Set<String>> rolesByUser = getRolesForStudyUsers(groupId);

        UserRoleResponse res = new UserRoleResponse();
        for (var entry: studyUsers.entrySet()) {
            UserInfo userInfo = entry.getValue();
            Set<String> roles = rolesByUser.get(entry.getKey());
            List<UserRole> userRoles = createUserRoles(roles, studyRoles);
            userInfo.addRoles(userRoles);
            res.addUser(userInfo);
        }

        return res;
    }

    protected static Map<Integer, UserInfo> getStudyUsers(int groupId, UserRequest req) {
        Map<Integer, UserInfo> allStudyUsers = getUsersForGroup(groupId);
        if (req == null || CollectionUtils.isEmpty(req.getUsers())) {
            return allStudyUsers;
        }

        Map<String, Integer> emailToId = allStudyUsers.entrySet().stream().collect(Collectors.toMap(e -> e.getValue().getEmail(),
                Map.Entry::getKey));

        Map<Integer, UserInfo> users = new HashMap<>();
        for (String email: req.getUsers()) {
            Integer id = emailToId.get(email);
            if (id == null) {
                throw new DSMBadRequestException("Invalid user email " + email);
            }
            users.put(id, allStudyUsers.get(id));
        }
        return users;
    }

    protected static List<UserRole> createUserRoles(Set<String> roles, Map<String, RoleInfo> studyRoles) {
        List<UserRole> userRoles = new ArrayList<>();

        for (var entry: studyRoles.entrySet()) {
            RoleInfo roleInfo = entry.getValue();
            userRoles.add(new UserRole(roleInfo.name, roleInfo.displayText, roles.contains(entry.getKey())));
        }
        return userRoles;
    }

    public void addStudyRole(StudyRoleRequest req) {
        int groupId = verifyStudyGroup(studyGroup);

        Map<String, RoleInfo> adminRoles = getAdminRoles(groupId);
        if (!adminRoles.containsKey(PEPPER_ADMIN_ROLE)) {
            throw new DSMBadRequestException("Operator does not have add study role privileges for study group "
                    + studyGroup);
        }

        List<StudyRoleRequest.RoleInfo> roles = req.getRoles();
        if (CollectionUtils.isEmpty(roles)) {
            throw new DSMBadRequestException("No roles provided");
        }

        for (StudyRoleRequest.RoleInfo roleInfo: roles) {
            String role = roleInfo.roleName;
            if (StringUtils.isBlank(role)) {
                throw new DSMBadRequestException("Invalid role name: blank");
            }
            int roleId = getRoleId(role);
            if (roleId == -1) {
                throw new DSMBadRequestException("Invalid role name: " + role);
            }

            int adminRoleId = -1;
            String adminRole = roleInfo.adminRoleName;
            if (!StringUtils.isBlank(adminRole)) {
                adminRoleId = getRoleId(adminRole);
                if (adminRoleId == -1) {
                    throw new DSMBadRequestException("Invalid admin role name: " + adminRole);
                }
            }
            addGroupRole(groupId, roleId, adminRoleId);
        }
    }

    protected Map<String, RoleInfo> getAdminRoles(int studyGroupId) {
        int adminId;
        try {
            adminId = Integer.parseInt(operatorId);
        } catch (NumberFormatException e) {
            throw new DSMBadRequestException("Invalid operator ID format: " + operatorId);
        }
        Map<String, RoleInfo> adminRoles = verifyOperatorGroup(adminId, studyGroupId);
        if (adminRoles == null || adminRoles.isEmpty()) {
            throw new DSMBadRequestException("Operator does not have user administrator privileges for study group "
                    + studyGroup);
        }
        return adminRoles;
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

    protected static int verifyStudyGroup(String studyGroup) {
        if (StringUtils.isBlank(studyGroup)) {
            throw new DSMBadRequestException("Invalid study group: blank");
        }
        return inTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_STUDY_GROUP)) {
                stmt.setString(1, studyGroup);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    } else {
                        throw new DSMBadRequestException("Invalid study group " + studyGroup);
                    }
                }
            } catch (SQLException e) {
                throw new DsmInternalError("Error getting study group", e);
            }
        });
    }

    protected static Map<String, RoleInfo> verifyOperatorGroup(int operatorId, int groupId) {
        Map<String, RoleInfo> adminRoles = null;
        List<String> roles = getRolesForUser(operatorId, groupId);
        if (roles.isEmpty()) {
            log.info("No roles found for operator {} and group {}", operatorId, groupId);
            return adminRoles;
        }

        if (roles.contains(PEPPER_ADMIN_ROLE)) {
            adminRoles = getAllRolesForStudy(groupId);
        } else if (roles.contains(USER_ADMIN_ROLE)) {
            adminRoles = getRolesForAdmin(groupId, USER_ADMIN_ROLE);
        }

        return adminRoles;
    }

    protected static List<String> getRolesForUser(int userId, int groupId) {
        return inTransaction(conn -> {
            List<String> roles = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_ROLES_FOR_USER)) {
                stmt.setInt(1, userId);
                stmt.setInt(2, groupId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        roles.add(rs.getString(2));
                    }
                }
            } catch (SQLException e) {
                throw new DsmInternalError("Error getting roles groups for user", e);
            }
            return roles;
        });
    }

    protected static Map<String, RoleInfo> getAllRolesForStudy(int groupId) {
        return inTransaction(conn -> {
            Map<String, RoleInfo> roles = new HashMap<>();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_STUDY_ROLES)) {
                stmt.setInt(1, groupId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        // TODO: temp until we have display text
                        String name = rs.getString(2);
                        RoleInfo info = new RoleInfo(rs.getInt(1), name, name);
                        roles.put(name, info);
                    }
                }
            } catch (SQLException e) {
                throw new DsmInternalError("Error getting roles for study group", e);
            }
            return roles;
        });
    }


    protected static Map<String, RoleInfo> getRolesForAdmin(int groupId, String adminRole) {
        return inTransaction(conn -> {
            Map<String, RoleInfo> roles = new HashMap<>();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_STUDY_ROLES_FOR_ADMIN)) {
                stmt.setInt(1, groupId);
                stmt.setString(2, adminRole);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        // TODO: temp until we have display text
                        String name = rs.getString(2);
                        RoleInfo info = new RoleInfo(rs.getInt(1), name, name);
                        roles.put(name, info);
                    }
                }
            } catch (SQLException e) {
                throw new DsmInternalError("Error getting study group roles for admin", e);
            }
            return roles;
        });
    }

    protected static void setAdminForRole(int roleId, int adminRoleId, int groupId) {

    }

    protected int verifyRoleForAdmin(String role, Map<String, String> adminRoles) {
        int roleId = getRoleId(role);
        if (roleId == -1 || !adminRoles.containsKey(role)) {
            String msg = String.format("Invalid role %s for study group %s", role, studyGroup);
            if (roleId != -1) {
                log.info("{}: role is valid but admin does not have permission", msg);
            }
            throw new DSMBadRequestException(msg);
        }
        return roleId;
    }

    protected static Map<Integer, Set<String>> getRolesForStudyUsers(int groupId) {
        return inTransaction(conn -> {
            Map<Integer, Set<String>> roles = new HashMap<>();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_ROLES_FOR_STUDY_USERS)) {
                stmt.setInt(1, groupId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        int userId = rs.getInt(1);
                        String role = rs.getString(2);
                        Set<String> userRoles = roles.computeIfAbsent(userId, k -> new HashSet<>());
                        userRoles.add(role);
                    }
                }
            } catch (SQLException e) {
                throw new DsmInternalError("Error getting user roles for study group " + groupId, e);
            }
            return roles;
        });
    }

    protected static int getRoleId(String role) {
        return inTransaction(conn -> {
            int id = -1;
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_ROLE)) {
                stmt.setString(1, role);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        id = rs.getInt(1);
                    }
                }
            } catch (SQLException e) {
                String msg = String.format("Error getting role %s", role);
                throw new DsmInternalError(msg, e);
            }
            return id;
        });
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

    protected static Map<Integer, UserInfo> getUsersForGroup(int groupId) {
        return inTransaction(conn -> {
            Map<Integer, UserInfo> res = new HashMap<>();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_USERS_FOR_GROUP)) {
                stmt.setInt(1, groupId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        int roleId = rs.getInt(1);
                        UserInfo info = new UserInfo(rs.getString(2), rs.getString(3), rs.getString(4));
                        res.put(roleId, info);
                    }
                }
            } catch (SQLException e) {
                throw new DsmInternalError("Error getting user info for group " + groupId, e);
            }
            return res;
        });
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

    protected static int addGroupRole(int groupId, int roleId, int adminRoleId) {
        String errMsg = "Error adding group role: groupId=%d, roleId=%d, adminRoleId=%d. ";
        int res = inTransaction(conn -> {
            int id = -1;
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_GROUP_ROLE, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1, groupId);
                stmt.setInt(2, roleId);
                if (adminRoleId == -1) {
                    stmt.setNull(3, Types.INTEGER);
                } else {
                    stmt.setInt(3, adminRoleId);
                }
                int result = stmt.executeUpdate();
                if (result != 1) {
                    throw new DsmInternalError(errMsg + "Result count for addGroupRole was " + result);
                }
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        id = rs.getInt(1);
                    }
                }
            } catch (SQLException ex) {
                throw new DsmInternalError(errMsg, ex);
            }
            return id;
        });

        if (res == -1) {
            throw new DsmInternalError(errMsg);
        }
        return res;
    }

    protected static int addStudyGroup(String groupName) {
        SimpleResult res = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_GROUP, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, groupName);
                int result = stmt.executeUpdate();
                if (result != 1) {
                    dbVals.resultException = new DsmInternalError("Result count for addStudyGroup was " + result);
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

    protected static int deleteGroupRole(int groupRoleId) {
        return inTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(SQL_DELETE_GROUP_ROLE)) {
                stmt.setInt(1, groupRoleId);
                return stmt.executeUpdate();
            } catch (SQLException ex) {
                String msg = String.format("Error deleting group role: groupRoleId=%d", groupRoleId);
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


    protected static class RoleInfo {
        public final int roleId;
        public final String name;
        public final String displayText;

        public RoleInfo(int roleId, String name, String displayText) {
            this.roleId = roleId;
            this.name = name;
            this.displayText = displayText;
        }
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
