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

import liquibase.pro.packaged.S;
import lombok.Data;
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

    private static final String SQL_SELECT_USER_ROLE =
            "SELECT aurg.user_role_group_id FROM access_user_role_group aurg "
                    + "JOIN access_role ar on ar.role_id = aurg.role_id "
                    + "JOIN ddp_group dg on dg.group_id = aurg.group_id "
                    + "JOIN access_user au on au.user_id = aurg.user_id "
                    + "WHERE au.user_id = ? AND ar.role_id = ? AND dg.group_id = ?";

    private static final String SQL_SELECT_ROLE =
            "SELECT ar.role_id FROM access_role ar WHERE ar.name = ?";

    private static final String SQL_SELECT_GROUP_ROLE =
            "SELECT dgr.group_role_id FROM ddp_group_role dgr WHERE dgr.group_id = ? AND dgr.role_id = ?";

    private static final String SQL_SELECT_USER_BY_EMAIL =
            "SELECT au.user_id, au.name, au.phone_number, au.is_active FROM access_user au WHERE au.email = ?";

    private static final String SQL_SELECT_USERS_FOR_GROUP =
            "SELECT au.user_id, au.email, au.name, au.phone_number FROM access_user au "
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

    private static final String SQL_INSERT_GROUP =
            "INSERT INTO ddp_group SET name = ?";

    private static final String SQL_DELETE_GROUP =
            "DELETE FROM ddp_group WHERE group_id = ?";

    private static final String SQL_INSERT_GROUP_ROLE =
            "INSERT INTO ddp_group_role SET group_id = ?, role_id = ?, admin_role_id = ?";

    private static final String SQL_SELECT_GROUP_FOR_REALM =
            "SELECT dg.group_id, dg.name from ddp_group dg "
                    + "JOIN ddp_instance_group dig on dig.ddp_group_id = dg.group_id "
                    + "JOIN ddp_instance di on di.ddp_instance_id = dig.ddp_instance_id "
                    + "WHERE di.instance_name = ?";

    public UserAdminService(String operatorId, String studyGroup) {
        this.operatorId = operatorId;
        this.studyGroup = studyGroup;
    }

    /**
     * Get study roles that operator can administer
     */
    public StudyRoleResponse getStudyRoles() {
        int groupId = verifyStudyGroup(studyGroup);
        Map<String, RoleInfo> studyRoles = getAdminRoles(groupId);

        StudyRoleResponse res = new StudyRoleResponse();
        for (var entry: studyRoles.entrySet()) {
            RoleInfo roleInfo = entry.getValue();
            res.addRole(new StudyRoleResponse.Role(roleInfo.getName(), roleInfo.getDisplayText()));
        }
        return res;
    }

    /**
     * Add roles for list of users
     */
    public void addUserRoles(UserRoleRequest req) {
        validateRoleRequest(req);

        int groupId = verifyStudyGroup(studyGroup);
        Map<String, RoleInfo> studyRoles = getAdminRoles(groupId);
        List<String> roleNames = req.getRoles();
        validateRoles(roleNames, studyRoles.keySet());

        for (String userEmail: req.getUsers()) {
            if (StringUtils.isBlank(userEmail)) {
                throw new DSMBadRequestException("Invalid user email: blank");
            }
            addRoles(userEmail, roleNames, groupId, studyRoles);
        }
    }

    protected void addRoles(String email, List<String> roles, int groupId, Map<String, RoleInfo> studyRoles) {
        int userId = verifyUserByEmail(email, groupId);

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

    /**
     * Remove roles for list of users
     */
    public void removeUserRoles(UserRoleRequest req) {
        validateRoleRequest(req);

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
        int userId = verifyUserByEmail(email, groupId);

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

    protected void validateRoleRequest(UserRoleRequest req) {
        if (CollectionUtils.isEmpty(req.getUsers())) {
            throw new DSMBadRequestException("Invalid users: empty");
        }
        if (CollectionUtils.isEmpty(req.getRoles())) {
            throw new DSMBadRequestException("Invalid roles: empty");
        }
    }

    protected void validateRoles(List<String> roleNames, Set<String> validRoleNames) {
        if (validRoleNames.containsAll(roleNames)) {
            return;
        }
        Collection<String> badRoles = CollectionUtils.subtract(roleNames, validRoleNames);
        String msg = String.format("Invalid roles for study group %s: %s", studyGroup, String.join(",", badRoles));
        throw new DSMBadRequestException(msg);
    }

    protected String validateEmailRequest(String email) {
        if (StringUtils.isBlank(email)) {
            throw new DSMBadRequestException("Invalid user email: blank");
        }
        return email;
    }

    /**
     * Add user to study group. User request must include at least one role
     */
    public void addUser(AddUserRequest req) {
        List<AddUserRequest.User> users = req.getUsers();
        if (CollectionUtils.isEmpty(users)) {
            throw new DSMBadRequestException("Invalid user list: blank");
        }

        // pre-check to lessen likelihood of partial operation
        for (var user: users) {
            String email = validateEmailRequest(user.getEmail());

            // not a strict requirement in DB, but now enforcing
            if (StringUtils.isBlank(user.getName())) {
                throw new DSMBadRequestException("Invalid user name: blank");
            }
            if (getUserByEmail(email, -1) != null) {
                throw new DsmInternalError("User already exists: " + email);
            }
        }

        UserDao userDao = new UserDao();
        for (var user: users) {
            int userId = userDao.create(user.asUserDto());
            if (userId == -1) {
                throw new DsmInternalError("Error creating user: " + user.getEmail());
            }
        }
    }

    /**
     * Remove one or more users and their associated roles
     */
    public void removeUser(UserRequest req) {
        if (CollectionUtils.isEmpty(req.getUsers())) {
            throw new DSMBadRequestException("Invalid user list: blank");
        }

        // pre-check all users to decrease likelihood of incomplete operation
        List<Integer> userIds = new ArrayList<>();
        for (String email: req.getUsers()) {
            validateEmailRequest(email);
            userIds.add(verifyUserByEmail(email, -1));
        }

        UserDao userDao = new UserDao();
        for (int userId: userIds) {
            deleteUserRoles(userId);
            userDao.delete(userId);
        }
    }

    /**
     * Return user information and user roles, both assigned and unassigned roles for study
     *
     * @param req list of users, or all study users if NULL
     */
    public UserRoleResponse getUserRoles(UserRequest req) {
        int groupId = verifyStudyGroup(studyGroup);
        Map<String, RoleInfo> studyRoles = getAdminRoles(groupId);

        // get userId to UserInfo for the users
        // if list of users provided, get roles for just those users
        Map<Integer, UserInfo> studyUsers = getStudyUsers(groupId, req);
        // get current roles for all study users
        Map<Integer, Set<String>> rolesByUser = getRolesForStudyUsers(groupId);

        // combine user info and role info
        UserRoleResponse res = new UserRoleResponse();
        for (var entry: studyUsers.entrySet()) {
            Set<String> roles = rolesByUser.get(entry.getKey());
            if (roles == null) {
                roles = new HashSet<>();
            }
            List<UserRole> userRoles = convertToUserRoles(roles, studyRoles);
            UserInfo userInfo = entry.getValue();
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
            if (id != null) {
                users.put(id, allStudyUsers.get(id));
            } else {
                // theoretically there should be no users without roles for a given study
                // but there are ways that might occur.
                log.warn("Found user with no study roles: {}", email);
                StudyUser user = getUserByEmail(email, groupId);
                if (user == null) {
                    throw new DSMBadRequestException("Invalid user email " + email);
                }
                users.put(user.getId(), user.toUserInfo());
            }
        }
        return users;
    }

    protected static List<UserRole> convertToUserRoles(Set<String> roles, Map<String, RoleInfo> studyRoles) {
        List<UserRole> userRoles = new ArrayList<>();

        for (var entry: studyRoles.entrySet()) {
            RoleInfo roleInfo = entry.getValue();
            userRoles.add(new UserRole(roleInfo.name, roleInfo.displayText, roles.contains(entry.getKey())));
        }
        return userRoles;
    }

    protected Map<String, RoleInfo> getAdminRoles(int studyGroupId) {
        int adminId;
        try {
            adminId = Integer.parseInt(operatorId);
        } catch (NumberFormatException e) {
            throw new DSMBadRequestException("Invalid operator ID format: " + operatorId);
        }
        return verifyOperatorAdminRoles(adminId, studyGroupId);
    }

    /**
     * Get valid study group based on 'realm' or 'studyGroup' query parameters
     *
     * @param queryParams HTTP request query params
     * @return valid study name
     * @throws DSMBadRequestException if 'realm' or 'studyGroup' are invalid or mismatched
     */
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
        NameAndId res = studyGroupForRealm(realm);
        if (res == null) {
            throw new DSMBadRequestException("Invalid realm: " + realm);
        }
        return res.name;
    }

    private static NameAndId studyGroupForRealm(String realm) {
        return inTransaction(conn -> {
            NameAndId res = null;
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_GROUP_FOR_REALM)) {
                stmt.setString(1, realm);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        res = new NameAndId(rs.getString(2), rs.getInt(1));
                    }
                }
            } catch (SQLException e) {
                String msg = String.format("Error getting study group for realm %s", realm);
                throw new DsmInternalError(msg, e);
            }
            return res;
        });
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

    protected Map<String, RoleInfo> verifyOperatorAdminRoles(int operatorId, int groupId) {
        Map<String, RoleInfo> adminRoles = getOperatorAdminRoles(operatorId, groupId);
        if (adminRoles == null || adminRoles.isEmpty()) {
            throw new DSMBadRequestException("Operator does not have user administrator privileges for study group "
                    + studyGroup);
        }
        return adminRoles;
    }

    protected static Map<String, RoleInfo> getOperatorAdminRoles(int operatorId, int groupId) {
        Map<String, RoleInfo> adminRoles = null;
        List<String> roles = getRolesForUser(operatorId, groupId);
        if (roles.isEmpty()) {
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

    protected static int verifyUserByEmail(String email, int groupId) {
        StudyUser user = getUserByEmail(email, groupId);
        // TODO: see if access_user.is_active is used -DC
        if (user == null) {
            throw new DSMBadRequestException("Invalid user for study group: " + email);
        }
        return user.getId();
    }

    protected static UserAdminService.StudyUser getUserByEmail(String email, int groupId) {
        // TODO: Currently we do not track users for a group, but get by groupId once we do -DC
        return inTransaction(conn -> {
            UserAdminService.StudyUser user = null;
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_USER_BY_EMAIL)) {
                stmt.setString(1, email);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        user = new StudyUser(rs.getInt(1), email, rs.getString(2), rs.getString(3));
                    }
                }
            } catch (SQLException e) {
                String msg = String.format("Error getting user %s for study group, ID: %d", email, groupId);
                throw new DsmInternalError(msg, e);
            }
            return user;
        });
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

    protected static int addUserRole(int userId, int roleId, int groupId) {
        // since we do not have a key constraint for duplicate entries we need to check first
        int userRoleId = getUserRole(userId, roleId, groupId);
        if (userRoleId != -1) {
            return userRoleId;
        }
        return inTransaction(conn -> {
            int id = -1;
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_USER_ROLE, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1, userId);
                stmt.setInt(2, roleId);
                stmt.setInt(3, groupId);
                int result = stmt.executeUpdate();
                if (result != 1) {
                    throw new DsmInternalError("Error adding user to role. Result count was " + result);
                }
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        id = rs.getInt(1);
                    }
                }
            } catch (SQLException ex) {
                throw new DsmInternalError("Error adding user to role", ex);
            }
            return id;
        });
    }

    protected static int getGroupRole(int groupId, int roleId) {
        return inTransaction(conn -> {
            int id = -1;
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_GROUP_ROLE)) {
                stmt.setInt(1, groupId);
                stmt.setInt(2, roleId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        id = rs.getInt(1);
                    }
                }
            } catch (SQLException e) {
                String msg = String.format("Error getting group role: groupId=%d, roleId=%s", groupId, roleId);
                throw new DsmInternalError(msg, e);
            }
            return id;
        });
    }

    protected static int addGroupRole(int groupId, int roleId, int adminRoleId) {
        String errMsg = String.format("Error adding group role: groupId=%d, roleId=%d, adminRoleId=%d. ",
                groupId, roleId, adminRoleId);
        int groupRoleId = getGroupRole(groupId, roleId);
        if (groupRoleId != -1) {
            throw new DsmInternalError(errMsg + "Group role already exists");
        }
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


    @Data
    protected static class RoleInfo {
        private final int roleId;
        private final String name;
        private final String displayText;

        public RoleInfo(int roleId, String name, String displayText) {
            this.roleId = roleId;
            this.name = name;
            this.displayText = displayText;
        }
    }

    @Data
    protected static class StudyUser {
        private final int id;
        private final String email;
        private final String name;
        private final String phone;

        public StudyUser(int id, String email, String name, String phone) {
            this.id = id;
            this.email = email;
            this.name = name;
            this.phone = phone;
        }

        public UserInfo toUserInfo() {
            return new UserInfo(email, name, phone);
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
