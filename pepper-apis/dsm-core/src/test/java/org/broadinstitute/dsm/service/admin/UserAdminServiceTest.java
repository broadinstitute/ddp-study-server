package org.broadinstitute.dsm.service.admin;

import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;
import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;
import static org.broadinstitute.dsm.service.admin.UserAdminService.PEPPER_ADMIN_ROLE;
import static org.broadinstitute.dsm.service.admin.UserAdminService.USER_ADMIN_ROLE;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.DbTxnBaseTest;
import org.broadinstitute.dsm.db.dao.user.UserDao;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.lddp.db.SimpleResult;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

@Slf4j
public class UserAdminServiceTest extends DbTxnBaseTest {

    private static final Map<Integer, List<Integer>> createdUserRoles = new HashMap<>();
    private static List<Integer> createdGroupRoles = new ArrayList<>();
    private static final String TEST_GROUP = "test_group";
    private static final String TEST_INSTANCE = "test_instance";
    private static int studyGroupId;
    private static int ddpInstanceId;
    private static int pepperAdminRoleId;
    private static int userAdminRoleId;

    private static final String SQL_INSERT_DDP_INSTANCE =
            "INSERT INTO ddp_instance SET instance_name = ?, is_active = 1, auth0_token = 1, migrated_ddp = 0";

    private static final String SQL_INSERT_DDP_INSTANCE_GROUP =
            "INSERT INTO ddp_instance_group SET ddp_instance_id = ?, ddp_group_id = ?";

    private static final String SQL_DELETE_DDP_INSTANCE_GROUP =
            "DELETE FROM ddp_instance_group WHERE ddp_instance_id = ?";

    private static final String SQL_DELETE_DDP_INSTANCE =
            "DELETE FROM ddp_instance WHERE ddp_instance_id = ?";

    @BeforeClass
    public static void setup() {
        studyGroupId = UserAdminService.addStudyGroup(TEST_GROUP);
        ddpInstanceId = createTestInstance(TEST_INSTANCE, studyGroupId);
        pepperAdminRoleId = UserAdminService.getRoleId(PEPPER_ADMIN_ROLE);
        Assert.assertTrue(pepperAdminRoleId != -1);
        userAdminRoleId = UserAdminService.getRoleId(USER_ADMIN_ROLE);
        Assert.assertTrue(userAdminRoleId != -1);
    }

    @AfterClass
    public static void tearDown() {
        UserDao userDao = new UserDao();
        for (var entry: createdUserRoles.entrySet()) {
            int userId = entry.getKey();
            List<Integer> userRoles = entry.getValue();
            for (int userRole: userRoles) {
                UserAdminService.deleteUserRole(userId, userRole, studyGroupId);
            }
            userDao.delete(userId);
        }
        deleteInstance(ddpInstanceId);
        UserAdminService.deleteStudyGroup(studyGroupId);
    }

    @After
    public void cleanup() {
        for (int groupRoleId: createdGroupRoles) {
            UserAdminService.deleteGroupRole(groupRoleId);
        }
    }

    private int createTestUser(String email, int roleId) {
        int userId = createUser(email);
        List<Integer> roleIds = new ArrayList<>();
        if (roleId != -1) {
            roleIds.add(roleId);
        }
        createdUserRoles.put(userId, roleIds);
        return userId;
    }

    private int createUser(String email) {
        String name = email.split("@")[0];
        UserDto userDto = new UserDto();
        userDto.setName(name);
        userDto.setEmail(email);
        userDto.setIsActive(1);
        UserDao userDao = new UserDao();
        return userDao.create(userDto);
    }

    private int addUserRole(int userId, int roleId, int groupId) {
        int userRoleId = UserAdminService.addUserRole(userId, roleId, groupId);
        addRoleForUser(roleId, userId);
        return userRoleId;
    }

    private void addRoleForUser(int roleId, int userId) {
        List<Integer> roleIds = createdUserRoles.get(userId);
        List<Integer> newRoleIds = new ArrayList<>(roleIds);
        newRoleIds.add(roleId);
        createdUserRoles.put(userId, newRoleIds);
    }

    private void removeRoleForUser(int roleId, int userId) {
        List<Integer> roleIds = createdUserRoles.get(userId);
        List<Integer> newRoleIds = new ArrayList<>(roleIds);
        newRoleIds.removeAll(List.of(roleId));
        createdUserRoles.put(userId, newRoleIds);
    }

    private int addGroupRole(int roleId, int adminRoleId) {
        int groupRoleId = UserAdminService.addGroupRole(studyGroupId, roleId, adminRoleId);
        createdGroupRoles.add(groupRoleId);
        return groupRoleId;
    }

    private void removeGroupRole(int groupRoleId) {
        UserAdminService.deleteGroupRole(groupRoleId);
        createdGroupRoles.remove(groupRoleId);
    }

    public void testGetRoleId() {
        // temporary to understand which roles are handled by liquibase
        log.info("TEMP: all roles: {}", String.join("\n", getAllRoles()));
        int roleId = UserAdminService.getRoleId("upload_onc_history");
        Assert.assertTrue(roleId > 0);
    }

    @Test
    public void testVerifyOperatorGroup() {
        int roleId = UserAdminService.getRoleId("dashboard_view");
        Assert.assertTrue(roleId > 0);
        int userId = createTestUser("test_admin1@study.org", roleId);
        int groupId = UserAdminService.verifyStudyGroup(TEST_GROUP);

        UserAdminService service = new UserAdminService(Integer.toString(userId), TEST_GROUP);
        try {
            service.verifyOperatorAdminRoles(userId, groupId);
            Assert.fail("Expecting exception from UserAdminService.verifyOperatorForGroup");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("does not have user administrator privileges"));
        }

        try {
            addUserRole(userId, roleId, groupId);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.addUserRole: " +  getStackTrace(e));
        }

        try {
            Map<String, UserAdminService.RoleInfo> adminRoles = UserAdminService.getOperatorAdminRoles(userId, groupId);
            Assert.assertTrue(adminRoles == null || adminRoles.isEmpty());
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.getOperatorAdminRoles: " +  getStackTrace(e));
        }

        try {
            service.verifyOperatorAdminRoles(userId, groupId);
            Assert.fail("Expecting exception from UserAdminService.verifyOperatorForGroup");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("does not have user administrator privileges"));
        }

        // give the admin user admin privileges, and a role to manage
        try {
            addUserRole(userId, userAdminRoleId, groupId);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.addUserRole: " +  getStackTrace(e));
        }

        addGroupRole(roleId, userAdminRoleId);

        try {
            service.verifyOperatorAdminRoles(userId, groupId);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.verifyOperatorForGroup: " +  getStackTrace(e));
        }
    }

    @Test
    public void testGetUserByEmailAndGroup() {
        int roleId = UserAdminService.getRoleId("upload_onc_history");
        Assert.assertTrue(roleId > 0);
        String email = "testUser@study.org";
        int userId = createTestUser(email, -1);
        int groupId = UserAdminService.verifyStudyGroup(TEST_GROUP);
        try {
            int id = UserAdminService.verifyUserByEmail(email, groupId);
            Assert.assertEquals(userId, id);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.getUserByEmail: " +  getStackTrace(e));
        }

        // we don't have coverage for this elsewhere
        UserDao userDao = new UserDao();
        try {
            Optional<UserDto> res = userDao.getUserByEmail(email);
            Assert.assertTrue(res.isPresent());
            Assert.assertEquals(userId, res.get().getId());
        } catch (Exception e) {
            Assert.fail("Exception from UserDao.getUserByEmail: " +  getStackTrace(e));
        }
        try {
            Optional<UserDto> res = userDao.get(userId);
            Assert.assertTrue(res.isPresent());
            Assert.assertEquals(userId, res.get().getId());
        } catch (Exception e) {
            Assert.fail("Exception from UserDao.get: " +  getStackTrace(e));
        }
    }

    @Test
    public void testValidateRoles() {
        UserAdminService service = new UserAdminService("not needed for test", TEST_GROUP);

        Set<String> studyRoles = Set.of("A", "B", "C");
        try {
            service.validateRoles(new ArrayList<>(), studyRoles);
            Assert.fail("UserAdminService.validateRoles should fail with no roles");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("Invalid roles for study group"));
            Assert.assertTrue(e.getMessage().contains("None provided"));
        }

        try {
            service.validateRoles(List.of("A", "", "B"), studyRoles);
            Assert.fail("UserAdminService.validateRoles should fail with blank role");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("Invalid roles for study group"));
            Assert.assertTrue(e.getMessage().contains("<blank>"));
        }

        try {
            service.validateRoles(List.of("A", "XXX", "B"), studyRoles);
            Assert.fail("UserAdminService.validateRoles should fail with invalid role for study");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("Invalid roles for study group"));
            Assert.assertTrue(e.getMessage().contains("XXX"));
        }

        try {
            service.validateRoles(List.of("A", "B"), studyRoles);
        } catch (Exception e) {
            Assert.fail("UserAdminService.validateRoles should not fail with valid roles for study");
        }
    }

    @Test
    public void testUserRoles() {
        String role1 = "upload_onc_history";
        String role2 = "upload_ror_file";
        List<String> roles = List.of(role1, role2);
        Map<String, Integer> rolesToId = getRoleIds(roles);
        String user1 = "testUser2@study.org";
        String user2 = "testUser3@study.org";
        List<String> users = List.of(user1, user2);
        Map<String, Integer> usersToId = setupUsers(users, rolesToId.values());

        int groupId = UserAdminService.verifyStudyGroup(TEST_GROUP);

        // let operator manage one of the roles
        int operatorId = setupAdmin("test_admin2@study.org", List.of(rolesToId.get(role1)), groupId);

        UserAdminService service = new UserAdminService(Integer.toString(operatorId), TEST_GROUP);

        UserRoleRequest req = new UserRoleRequest(users, roles);
        try {
            service.addUserRoles(req);
            Assert.fail("UserAdminService.addUserToRoles should fail with roles not in study");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("Invalid roles for study group"));
        }

        // both roles are now in the study
        addGroupRole(rolesToId.get(role2), userAdminRoleId);

        // verify by getting all study roles
        Set<String> allRoles = Set.of(role1, role2);
        StudyRoleResponse srRes = service.getStudyRoles();
        Assert.assertEquals(srRes.getRoles().stream().map(StudyRoleResponse.Role::getName).collect(Collectors.toSet()), allRoles);

        try {
            service.addUserRoles(req);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.addUserToRole: " +  getStackTrace(e));
        }

        // verify internally
        for (var userId: usersToId.values()) {
            for (var roleId: rolesToId.values()) {
                verifyUserHasRole(userId, roleId, groupId);
            }
        }

        // get roles and verify
        UserRequest getReq = new UserRequest(users);
        UserRoleResponse res = null;
        try {
            res = service.getUserRoles(getReq);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.getUserRoles: " +  getStackTrace(e));
        }

        List<UserInfo> userInfoList = res.getUsers();
        Map<String, Map<String, Boolean>> userRoles = getUserRoles(userInfoList);
        verifyResponseRoles(userRoles.get(user1), List.of(role1, role2), List.of(role1, role2));
        verifyResponseRoles(userRoles.get(user2), List.of(role1, role2), List.of(role1, role2));

        // remove one role for both users
        UserRoleRequest req2 = new UserRoleRequest(users, List.of(role1));
        try {
            service.removeUserRoles(req2);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.removeUserFromRoles: " +  getStackTrace(e));
        }

        // get roles and verify
        try {
            res = service.getUserRoles(getReq);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.getUserRoles: " +  getStackTrace(e));
        }

        userInfoList = res.getUsers();
        userRoles = getUserRoles(userInfoList);
        verifyResponseRoles(userRoles.get(user1), List.of(role1, role2), List.of(role2));
        verifyResponseRoles(userRoles.get(user2), List.of(role1, role2), List.of(role2));

        // check that result also has unassigned roles
        for (var userInfo: userInfoList) {
            Assert.assertEquals(userInfo.getRoles().stream().map(UserRole::getName).collect(Collectors.toSet()), allRoles);
        }

        // adjust cleanup
        removeRoleForUser(rolesToId.get(role1), usersToId.get(user1));
        removeRoleForUser(rolesToId.get(role1), usersToId.get(user2));

        // remove last role for one user, which should not be allowed
        UserRoleRequest req3 = new UserRoleRequest(List.of(user2), List.of(role2));
        try {
            service.removeUserRoles(req3);
            Assert.fail("UserAdminService.removeUserRoles should fail to remove all user roles");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("Cannot remove all roles for user"));
        }
    }

    private static void verifyUserHasRole(int userId, int roleId, int groupId) {
        int userRoleId = UserAdminService.getUserRole(userId, roleId, groupId);
        Assert.assertNotEquals(-1, userRoleId);
    }

    private static void verifyResponseRoles(Map<String, Boolean> userRoles, List<String> allRoles, List<String> assignedRoles) {
        Assert.assertTrue(allRoles.containsAll(assignedRoles));

        for (String role: assignedRoles) {
            Boolean isAssigned = userRoles.get(role);
            Assert.assertNotNull("GetUserRoles did not include role", isAssigned);
            Assert.assertEquals(assignedRoles.contains(role), isAssigned);
        }
    }

    @Test
    public void testAddAndRemoveUser() {
        int groupId = UserAdminService.verifyStudyGroup(TEST_GROUP);

        String role1 = "upload_onc_history";
        String role2 = "upload_ror_file";
        List<String> roles = List.of(role1, role2);
        Map<String, Integer> rolesToId = getRoleIds(roles);

        // include a role for the user, but let the admin service manage it via removeUser
        int operatorId = setupAdmin("test_admin3@study.org", List.of(rolesToId.get(role1)), groupId);
        addGroupRole(rolesToId.get(role2), userAdminRoleId);

        String user = "testUser4@study.org";
        String userName = "testUser4";
        AddUserRequest req = new AddUserRequest(List.of(new AddUserRequest.User(user, userName, null,
                List.of(role1))));

        UserAdminService service = new UserAdminService(Integer.toString(operatorId), TEST_GROUP);
        try {
            service.addUser(req);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.createUser: " +  getStackTrace(e));
        }

        // add a role
        UserRoleRequest roleReq = new UserRoleRequest(List.of(user), List.of(role2));
        try {
            service.addUserRoles(roleReq);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.addUserRoles: " +  getStackTrace(e));
        }

        // verify user info
        UserRoleResponse res = null;
        try {
            res = service.getUserRoles(null);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.getUserRoles: " +  getStackTrace(e));
        }

        List<UserInfo> userInfoList = res.getUsers();
        verifyUserInfo(userInfoList, user, userName, null);

        Map<String, Map<String, Boolean>> userRoles = getUserRoles(userInfoList);
        verifyResponseRoles(userRoles.get(user), List.of(role1), List.of(role1));

        // update user
        String newUserName = "newName";
        String phone = "555-1212";
        UpdateUserRequest.User updateUser = new UpdateUserRequest.User(user, newUserName, phone);
        UpdateUserRequest updateReq = new UpdateUserRequest(List.of(updateUser));
        try {
            service.updateUser(updateReq);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.updateUser: " +  getStackTrace(e));
        }

        // verify user info again
        try {
            res = service.getUserRoles(null);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.getUserRoles: " +  getStackTrace(e));
        }

        userInfoList = res.getUsers();
        verifyUserInfo(userInfoList, user, newUserName, phone);

        userRoles = getUserRoles(userInfoList);
        verifyResponseRoles(userRoles.get(user), List.of(role1, role2), List.of(role1, role2));

        UserRequest removeReq = new UserRequest(List.of(user));
        try {
            service.removeUser(removeReq);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.removeUser: " +  getStackTrace(e));
        }

        try {
            UserAdminService.verifyUserByEmail(user, -1);
            Assert.fail("UserAdminService.removeUser failed to remove user");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("Invalid user"));
        }
    }

    private static void verifyUserInfo(List<UserInfo> userInfoList, String email, String name, String phone) {
        UserInfo userInfo = null;
        for (UserInfo u: userInfoList) {
            if (u.getEmail().equals(email)) {
                userInfo = u;
                break;
            }
        }
        Assert.assertNotNull(userInfo);

        Assert.assertEquals(email, userInfo.getEmail());
        Assert.assertEquals(name, userInfo.getName());
        Assert.assertEquals(phone, userInfo.getPhone());
    }

    @Test
    public void testGetStudyGroup() {
        Map<String, String[]> qp = new HashMap<>();
        try {
            UserAdminService.getStudyGroup(qp);
            Assert.fail("Expecting exception from UserAdminService.getStudyGroup");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("Invalid"));
        }
        String[] groupName = {TEST_GROUP};
        String[] realmName = {TEST_INSTANCE};
        qp.put(RoutePath.STUDY_GROUP, groupName);
        Assert.assertEquals(TEST_GROUP, UserAdminService.getStudyGroup(qp));
        qp.put(RoutePath.REALM, realmName);
        Assert.assertEquals(TEST_GROUP, UserAdminService.getStudyGroup(qp));
        qp.remove(RoutePath.STUDY_GROUP);
        Assert.assertEquals(TEST_GROUP, UserAdminService.getStudyGroup(qp));
        try {
            String[] invalidGroupName = {"invalid_test_group"};
            qp.put(RoutePath.STUDY_GROUP, invalidGroupName);
            UserAdminService.getStudyGroup(qp);
            Assert.fail("Expecting exception from UserAdminService.getStudyGroup");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("does not match"));
        }
    }

    private int createAdminUser(String email, int adminRoleId) {
        int userId = createTestUser(email, adminRoleId);
        int groupId = UserAdminService.verifyStudyGroup(TEST_GROUP);
        try {
            addUserRole(userId, adminRoleId, groupId);
            log.info("Created admin user {} wih id {} and adminRoleId={}", email, userId, adminRoleId);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.addUserRole: " +  getStackTrace(e));
        }
        return userId;
    }

    private int setupAdmin(String adminEmail, List<Integer> rolesToManage, int groupId) {
        int operatorId = createAdminUser(adminEmail, userAdminRoleId);
        try {
            Map<String, UserAdminService.RoleInfo> adminRoles = UserAdminService.getOperatorAdminRoles(operatorId, groupId);
            Assert.assertTrue("adminRoles = " + adminRoles.keySet(), adminRoles.isEmpty());
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.verifyOperatorForGroup: " +  getStackTrace(e));
        }

        if (rolesToManage != null) {
            for (int roleId: rolesToManage) {
                addGroupRole(roleId, userAdminRoleId);
            }
        }
        return operatorId;
    }

    private Map<String, Integer> setupUsers(List<String> users, Collection<Integer> roleIds) {

        Map<String, Integer> userToId = new HashMap<>();
        for (String user: users) {
            int userId = createTestUser(user, -1);
            for (int roleId : roleIds) {
                addRoleForUser(roleId, userId);
            }
            userToId.put(user, userId);
        }
        return userToId;
    }


    private Map<String, Integer> getRoleIds(List<String> roles) {

        Map<String, Integer> roleToId = new HashMap<>();
        for (String role: roles) {
            int roleId = UserAdminService.getRoleId(role);
            Assert.assertTrue(roleId > 0);
            roleToId.put(role, roleId);
        }
        return roleToId;
    }

    private void addAdminRoles(int adminUserId, int groupId, List<String> roles) {
        try {
            for (String role : roles) {
                int roleId = UserAdminService.getRoleId(role);
                UserAdminService.addUserRole(adminUserId, roleId, groupId);
            }
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.addUserRole: " +  getStackTrace(e));
        }
    }

    private void setUserRoles(String email, List<String> roles, String studyGroup) {
        try {
            int userId = UserAdminService.verifyUserByEmail(email, -1);
            int groupId = UserAdminService.verifyStudyGroup(studyGroup);

            for (String role : roles) {
                int roleId = UserAdminService.getRoleId(role);
                UserAdminService.addUserRole(userId, roleId, groupId);
                String msg = String.format("Set up role %s for user %s in study group %s", role, email, studyGroup);
                log.info(msg);
            }
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.addUserRole: " +  getStackTrace(e));
        }
    }

    private static Map<String, Map<String, Boolean>> getUserRoles(List<UserInfo> userInfoList) {
        Map<String, Map<String, Boolean>> userRoles = new HashMap<>();
        for (UserInfo userInfo: userInfoList) {
            Map<String, Boolean> roles = userInfo.getRoles().stream()
                    .collect(Collectors.toMap(UserRole::getName, UserRole::isHasRole));
            userRoles.put(userInfo.getEmail(), roles);
        }
        return userRoles;
    }

    private static List<String> getAllRoles() {
        return inTransaction(conn -> {
            List<String> roles = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement("SELECT ar.role_id, ar.name FROM access_role ar")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        // TODO: temp until we have display text
                        roles.add(rs.getString(2));
                    }
                }
            } catch (SQLException e) {
                throw new DsmInternalError("Error getting roles", e);
            }
            return roles;
        });
    }

    private static int createTestInstance(String instanceName, int studyGroupId) {
        int instanceId = createInstance(instanceName);
        SimpleResult res = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_DDP_INSTANCE_GROUP, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1, instanceId);
                stmt.setInt(2, studyGroupId);
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
            throw new DsmInternalError("Error adding DDP instance group " + instanceName, res.resultException);
        }
        return instanceId;
    }

    private static int createInstance(String instanceName) {
        SimpleResult res = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_DDP_INSTANCE, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, instanceName);
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
            throw new DsmInternalError("Error adding DDP instance group " + instanceName, res.resultException);
        }
        return (int) res.resultValue;
    }

    private static void deleteInstance(int instanceId) {
        inTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(SQL_DELETE_DDP_INSTANCE_GROUP)) {
                stmt.setInt(1, instanceId);
                return stmt.executeUpdate();
            } catch (SQLException ex) {
                String msg = String.format("Error deleting DDP instance group: ddpInstanceId=%d", instanceId);
                throw new DsmInternalError(msg, ex);
            }
        });

        inTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(SQL_DELETE_DDP_INSTANCE)) {
                stmt.setInt(1, instanceId);
                return stmt.executeUpdate();
            } catch (SQLException ex) {
                String msg = String.format("Error deleting DDP instance: ddpInstanceId=%d", instanceId);
                throw new DsmInternalError(msg, ex);
            }
        });
    }
}
