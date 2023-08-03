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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.broadinstitute.dsm.db.UserSettings;
import org.broadinstitute.dsm.db.dao.user.UserDao;
import org.broadinstitute.dsm.db.dto.user.UserDto;
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
    private int adminId;
    private String adminEmail;
    private boolean initialized;

    private static final String SQL_SELECT_STUDY_GROUP =
            "SELECT dg.group_id FROM ddp_group dg WHERE dg.name = ?";

    private static final String SQL_SELECT_STUDY_ROLES =
            "SELECT ar.role_id, ar.name, ar.display_text FROM access_role ar "
                    + "JOIN ddp_group_role dgr on ar.role_id = dgr.role_id "
                    + "WHERE dgr.group_id = ?";

    private static final String SQL_SELECT_STUDY_ROLES_FOR_ADMIN =
            "SELECT ar.role_id, ar.name, ar.display_text FROM access_role ar "
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
        initialized = false;
    }

    protected void initialize() {
        if (initialized) {
            return;
        }
        try {
            adminId = Integer.parseInt(operatorId);
        } catch (NumberFormatException e) {
            throw new DSMBadRequestException("Invalid operator ID format: " + operatorId);
        }
        UserDao userDao = new UserDao();
        UserDto userDto = userDao.get(adminId).orElseThrow(() -> new DSMBadRequestException("Invalid operator ID " + operatorId));
        adminEmail = userDto.getEmailOrThrow();

        initialized = true;
    }

    /**
     * Get study roles that operator can administer
     */
    public StudyRoleResponse getStudyRoles() {
        initialize();
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
     * Set the roles for list of users
     */
    public void setUserRoles(SetUserRoleRequest req) {
        initialize();
        int groupId = verifyStudyGroup(studyGroup);

        Map<String, Integer> userIds = validateUsers(req.getUsers(), groupId);

        Map<String, RoleInfo> studyRoles = getAdminRoles(groupId);
        List<String> roleNames = req.getRoles();
        validateRoles(roleNames, studyRoles.keySet());

        // get existing user roles
        Map<String, List<String>> userRoles = new HashMap<>();
        for (var entry: userIds.entrySet()) {
            userRoles.put(entry.getKey(), getRolesForUser(entry.getValue(), groupId));
        }

        for (var entry: userIds.entrySet()) {
            List<String> existingRoles = userRoles.get(entry.getKey());
            Collection<String> rolesToAdd = CollectionUtils.subtract(roleNames, existingRoles);
            Collection<String> rolesToRemove = CollectionUtils.subtract(existingRoles, roleNames);
            int userId = entry.getValue();

            if (!rolesToAdd.isEmpty()) {
                addRoles(userId, rolesToAdd, groupId, studyRoles);
                log.info("[User admin] Operator {} added roles for user {} in study group {}: {}", adminEmail,
                        entry.getKey(), studyGroup, String.join(", ", rolesToAdd));
            }

            if (!rolesToRemove.isEmpty()) {
                removeUserRoles(userId, rolesToRemove, groupId, studyRoles);
                log.info("[User admin] Operator {} removed roles for user {} in study group {}: {}", adminEmail,
                        entry.getKey(), studyGroup, String.join(", ", rolesToRemove));
            }
        }
    }

    /**
     * Add and remove roles for list of users
     */
    public void updateUserRoles(UpdateUserRoleRequest req) {
        initialize();
        int groupId = verifyStudyGroup(studyGroup);

        if (CollectionUtils.isEmpty(req.getUsers())) {
            throw new DSMBadRequestException("Invalid users: empty");
        }

        List<String> addRoles = req.getAddRoles();
        List<String> removeRoles = req.getRemoveRoles();
        boolean hasAddRoles = CollectionUtils.isNotEmpty(addRoles);
        boolean hasRemoveRoles = CollectionUtils.isNotEmpty(removeRoles);

        if (hasAddRoles) {
            // ensure no union of add and remove roles
            if (hasRemoveRoles && CollectionUtils.containsAny(addRoles, removeRoles)) {
                throw new DSMBadRequestException("Invalid user roles request: Cannot add and remove the same roles");
            }
        } else if (!hasRemoveRoles) {
            throw new DSMBadRequestException("No update roles provided");
        }

        Map<String, Integer> userIds = validateUsers(req.getUsers(), groupId);

        // validate roles
        Map<String, RoleInfo> studyRoles = getAdminRoles(groupId);
        if (hasAddRoles) {
            validateRoles(addRoles, studyRoles.keySet());
        }
        if (hasRemoveRoles) {
            validateRoles(removeRoles, studyRoles.keySet());
            for (var entry: userIds.entrySet()) {
                List<String> existingRoles = getRolesForUser(entry.getValue(), groupId);
                if (CollectionUtils.subtract(existingRoles, removeRoles).isEmpty()) {
                    throw new DSMBadRequestException("Cannot remove all roles for user " + entry.getKey());
                }
            }
        }

        if (hasAddRoles) {
            for (var entry: userIds.entrySet()) {
                addRoles(entry.getValue(), addRoles, groupId, studyRoles);
                log.info("[User admin] Operator {} added roles for user {} in study group {}: {}", adminEmail,
                        entry.getKey(), studyGroup, String.join(", ", addRoles));
            }
        }

        if (hasRemoveRoles) {
            for (var entry: userIds.entrySet()) {
                removeUserRoles(entry.getValue(), removeRoles, groupId, studyRoles);
                log.info("[User admin] Operator {} removed roles for user {} in study group {}: {}", adminEmail,
                        entry.getKey(), studyGroup, String.join(", ", removeRoles));
            }
        }
    }

    /**
     * Add roles for user
     *
     * @param roles list of proposed roles, already validated
     */
    protected void addRoles(int userId, Collection<String> roles, int groupId, Map<String, RoleInfo> studyRoles) {
        for (String role : roles) {
            // role names already validated
            int roleId = studyRoles.get(role).roleId;
            try {
                addUserRole(userId, roleId, groupId);
            } catch (Exception e) {
                String msg = String.format("Error adding user %d to role %s for study group %s", userId, role, studyGroup);
                throw new DsmInternalError(msg, e);
            }
        }
    }

    /**
     * Remove roles for user
     *
     * @param roles list of proposed roles, already validated
     */
    protected void removeUserRoles(int userId, Collection<String> roles, int groupId, Map<String, RoleInfo> studyRoles) {
        for (String role : roles) {
            // role names already validated
            int roleId = studyRoles.get(role).roleId;
            try {
                // ignore failure to remove
                deleteUserRole(userId, roleId, groupId);
            } catch (Exception e) {
                String msg = String.format("Error removing user %d from role %s for study group %s", userId, role, studyGroup);
                throw new DsmInternalError(msg, e);
            }
        }
    }

    protected Map<String, Integer> validateUsers(List<String> users, int groupId) {
        if (CollectionUtils.isEmpty(users)) {
            throw new DSMBadRequestException("Invalid users: empty");
        }
        Map<String, Integer> userIds = new HashMap<>();
        for (String user: users) {
            UserDto userDto = verifyUserByEmail(user, groupId);
            userIds.put(userDto.getEmailOrThrow(), userDto.getId());
        }
        return userIds;
    }

    protected void validateRoles(List<String> roleNames, Set<String> validRoleNames) {
        String msg = String.format("Invalid roles for study group %s: ", studyGroup);
        if (CollectionUtils.isEmpty(roleNames)) {
            throw new DSMBadRequestException(msg + "None provided");
        }
        if (validRoleNames.containsAll(roleNames)) {
            return;
        }
        Collection<String> badRoles = CollectionUtils.subtract(roleNames, validRoleNames).stream()
                .map(r -> r.isEmpty() ? "<blank>" : r).collect(Collectors.toSet());
        throw new DSMBadRequestException(msg + String.join(", ", badRoles));
    }

    protected static String validateEmailRequest(String email) {
        if (StringUtils.isBlank(email)) {
            throw new DSMBadRequestException("Invalid user email: blank");
        }
        return email.trim();
    }

    public void addAndRemoveUsers(UserRequest req) {
        initialize();
        int groupId = verifyStudyGroup(studyGroup);
        Map<String, RoleInfo> studyRoles = getAdminRoles(groupId);

        List<String> removeUsers = req.getRemoveUsers();
        List<UserRequest.User> addUsers = req.getAddUsers();
        boolean hasAddUsers = !CollectionUtils.isEmpty(addUsers);
        boolean hasRemoveUsers = !CollectionUtils.isEmpty(removeUsers);

        // verify request data
        Map<String, UserDto> emailToAddUser = null;
        if (hasAddUsers) {
            emailToAddUser = verifyAddUser(addUsers, groupId, studyRoles.keySet());
        }

        List<UserDto> removeUserDto = new ArrayList<>();
        if (hasRemoveUsers) {
            for (String user: removeUsers) {
                removeUserDto.add(verifyUserByEmail(user, groupId));
            }

            // ensure no union of add and remove users
            if (hasAddUsers && CollectionUtils.containsAny(emailToAddUser.keySet(),
                    removeUserDto.stream().map(UserDto::getEmailOrThrow).collect(Collectors.toList()))) {
                throw new DSMBadRequestException("Invalid user request: Cannot add and remove the same user");
            }
        } else if (!hasAddUsers) {
            throw new DSMBadRequestException("Invalid user request: no users");
        }

        if (hasRemoveUsers) {
            removeUser(removeUserDto);
        }

        if (hasAddUsers) {
            addUser(addUsers, emailToAddUser, groupId, studyRoles);
        }
    }

    /**
     * Add user to study group. User request must include at least one role
     */
    protected void addUser(List<UserRequest.User> users, Map<String, UserDto> emailToUser, int groupId,
                           Map<String, RoleInfo> studyRoles) {
        UserDao userDao = new UserDao();
        for (var user: users) {
            // email already verified
            String email = user.getEmail();
            UserDto userDto = emailToUser.get(email);
            int userId = userDto.getId();
            boolean hasUserSettings = false;
            if (userId != 0) {
                UserDao.update(userId, userDto);
                hasUserSettings = UserSettings.getUserSettings(email) != null;
            } else {
                userId = userDao.create(userDto);
            }
            if (!hasUserSettings) {
                UserSettings.createUserSettings(userId);
            }
            addRoles(userId, user.getRoles(), groupId, studyRoles);
            log.info("[User admin] Operator {} added user {} to study group {} with roles {}", adminEmail, email,
                    studyGroup, String.join(", ", user.getRoles()));
        }
    }

    protected Map<String, UserDto> verifyAddUser(List<UserRequest.User> users, int groupId, Set<String> studyRoles) {
        Map<String, UserDto> emailToUser = new HashMap<>();
        for (var user: users) {
            String email = validateEmailRequest(user.getEmail());
            validateEmailFormat(email);
            // update email in request since we use it as a key
            user.setEmail(email);

            UserDto userDto = getUserByEmail(email, groupId);
            if (userDto != null) {
                // update any new info provided
                emailToUser.put(email, user.asUpdatedUserDto(verifyExistingUser(userDto, groupId)));
            } else {
                userDto = user.asUserDto();
                emailToUser.put(email, userDto);
            }

            // not a strict requirement in DB, but now enforcing
            if (StringUtils.isBlank(userDto.getName().orElse(null))) {
                throw new DSMBadRequestException("Invalid user name: blank");
            }

            validateRoles(user.getRoles(), studyRoles);
        }
        return emailToUser;
    }

    protected UserDto verifyExistingUser(UserDto userDto, int groupId) {
        String email = userDto.getEmailOrThrow();

        // if user has no roles in this study then it is okay to add them
        List<String> existingRoles = getRolesForUser(userDto.getId(), groupId);
        if (!existingRoles.isEmpty()) {
            throw new DSMBadRequestException(String.format("Cannot add user %s: Already has roles in study %s",
                    email, studyGroup));
        }

        if (userDto.isActive()) {
            log.info("addUser: user {} already exists, is active, but has no roles in study {}. "
                            + "Updating with new user information",
                    email, studyGroup);
        } else {
            log.info("addUser: user {} already exists but is inactive. Activating and updating with new user information",
                    email);
            userDto.setIsActive(1);
        }
        return userDto;
    }

    public void updateUser(UpdateUserRequest req) {
        initialize();
        int groupId = validateOperatorAdmin();
        List<UpdateUserRequest.User> users = req.getUsers();
        if (CollectionUtils.isEmpty(users)) {
            throw new DSMBadRequestException("Invalid user list: blank");
        }

        Map<Integer, UpdateUserRequest.User> usersById = new HashMap<>();
        // pre-check to lessen likelihood of partial operation
        for (UpdateUserRequest.User user: users) {
            // not a strict requirement in DB, but now enforcing
            if (StringUtils.isBlank(user.getName())) {
                throw new DSMBadRequestException("Invalid user name: blank");
            }
            UserDto userDto = verifyUserByEmail(user.getEmail(), groupId);
            user.setEmail(userDto.getEmailOrThrow());
            usersById.put(userDto.getId(), user);
        }

        for (var entry: usersById.entrySet()) {
            UserDto userDto = entry.getValue().asUserDto();
            UserDao.update(entry.getKey(), userDto);
            log.info("[User admin] Operator {} updated information for user {} in study group {}",
                    adminEmail, userDto.getEmailOrThrow(), studyGroup);
        }
    }

    /**
     * Remove one or more users and their associated roles
     */
    protected void removeUser(List<UserDto> userDto) {
        for (UserDto user: userDto) {
            deleteUserRoles(user.getId());
            user.setIsActive(0);
            UserDao.update(user.getId(), user);
            UserSettings.deleteUserSettings(user.getId());
            log.info("[User admin] Operator {} removed user {} from study group {}", adminEmail, user.getEmailOrThrow(),
                    studyGroup);
        }
    }

    protected int validateOperatorAdmin() {
        int groupId = verifyStudyGroup(studyGroup);
        // will throw if operator is not user admin
        getAdminRoles(groupId);
        return groupId;
    }

    /**
     * Return user information and user roles, both assigned and unassigned roles for study
     *
     * @param req list of users, or all study users if NULL
     */
    public UserRoleResponse getUserRoles(UserRoleRequest req) {
        initialize();
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
            userInfo.setRoles(userRoles);
            res.addUser(userInfo);
        }

        return res;
    }

    protected static Map<Integer, UserInfo> getStudyUsers(int groupId, UserRoleRequest req) {
        Map<Integer, UserInfo> allStudyUsers = getUsersForGroup(groupId);
        if (req == null || CollectionUtils.isEmpty(req.getUsers())) {
            return allStudyUsers;
        }

        Map<String, Integer> emailToId = allStudyUsers.entrySet().stream().collect(Collectors.toMap(e ->
                        e.getValue().getEmail().toUpperCase(), Map.Entry::getKey));
        Map<Integer, UserInfo> users = new HashMap<>();
        for (String email: req.getUsers()) {
            Integer id = emailToId.get(email.trim().toUpperCase());
            if (id != null) {
                users.put(id, allStudyUsers.get(id));
            } else {
                // theoretically there should be no users without roles for a given study
                // but there are ways that might occur.
                log.warn("Found user with no study roles: {}", email);
                UserDto user = verifyUserByEmail(email, groupId);
                users.put(user.getId(), new UserInfo(user));
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
                        String name = rs.getString(2);
                        String displayText = rs.getString(3);
                        RoleInfo info = new RoleInfo(rs.getInt(1), name,
                                StringUtils.isBlank(displayText) ? name : displayText);
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
                        String name = rs.getString(2);
                        String displayText = rs.getString(3);
                        RoleInfo info = new RoleInfo(rs.getInt(1), name,
                                StringUtils.isBlank(displayText) ? name : displayText);
                        roles.put(name, info);
                    }
                }
            } catch (SQLException e) {
                throw new DsmInternalError("Error getting study group roles for admin", e);
            }
            return roles;
        });
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

    protected static UserDto verifyUserByEmail(String email, int groupId) {
        email = validateEmailRequest(email);
        UserDto user = getUserByEmail(email, groupId);
        if (user == null) {
            throw new DSMBadRequestException("Invalid user for study group: " + email);
        }
        if (!user.isActive()) {
            throw new DSMBadRequestException("Invalid user for study group (inactive): " + email);
        }
        return user;
    }

    protected static void validateEmailFormat(String email) {
        if (!EmailValidator.getInstance().isValid(email)) {
            throw new DSMBadRequestException("Invalid email address format: " + email);
        }
    }

    protected static UserDto getUserByEmail(String email, int groupId) {
        // TODO: Currently we do not track users for a group, but get by groupId once we do -DC
        UserDao userDao = new UserDao();
        Optional<UserDto> userDto = userDao.getUserByEmail(email);
        return userDto.orElse(null);
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

    protected static class NameAndId {
        public final String name;
        public final int id;

        public NameAndId(String name, int id) {
            this.name = name;
            this.id = id;
        }
    }
}
