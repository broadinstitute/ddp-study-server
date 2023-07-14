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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.DbTxnBaseTest;
import org.broadinstitute.dsm.db.dao.user.UserDao;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.lddp.db.SimpleResult;
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
        for (int groupRoleId: createdGroupRoles) {
            UserAdminService.deleteGroupRole(groupRoleId);
        }

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

    private int addUserRole(int userId, int roleId, int groupId) throws Exception {
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

    @Test
    public void testgetRoleId() {
        int roleId = UserAdminService.getRoleId("upload_onc_history");
        Assert.assertTrue(roleId > 0);
    }

    @Test
    public void testVerifyOperatorGroup() {
        int roleId = UserAdminService.getRoleId("upload_onc_history");
        Assert.assertTrue(roleId > 0);
        int userId = createTestUser("test_admin1@study.org", roleId);
        int groupId = UserAdminService.verifyStudyGroup(TEST_GROUP);
        try {
            UserAdminService.verifyOperatorGroup(userId, groupId);
            Assert.fail("Expecting exception from UserAdminService.verifyOperatorForGroup");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("No roles found"));
        }

        try {
            addUserRole(userId, roleId, groupId);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.addUserRole: " +  getStackTrace(e));
        }

        try {
            Map<String, UserAdminService.RoleInfo> adminRoles = UserAdminService.verifyOperatorGroup(userId, groupId);
            Assert.assertTrue(adminRoles.isEmpty());
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.addUserRole: " +  getStackTrace(e));
        }

        try {
            Map<String, UserAdminService.RoleInfo> adminRoles = UserAdminService.verifyOperatorGroup(userId, groupId);
            Assert.fail("Expecting exception from UserAdminService.verifyOperatorForGroup");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("does not have administrator privileges"));
        }

        int adminRoleId = UserAdminService.getRoleId(USER_ADMIN_ROLE);
        try {
            addUserRole(userId, adminRoleId, groupId);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.addUserRole: " +  getStackTrace(e));
        }
        try {
            UserAdminService.verifyOperatorGroup(userId, groupId);
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
            int id = UserAdminService.getUserByEmail(email, groupId);
            Assert.assertEquals(userId, id);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.getUserByEmail: " +  getStackTrace(e));
        }
    }

    @Test
    public void testUserRoles() {
        int operatorId = createAdminUser("test_admin2@study.org", userAdminRoleId);
        int groupId = UserAdminService.verifyStudyGroup(TEST_GROUP);
        try {
            UserAdminService.verifyOperatorGroup(operatorId, groupId);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.verifyOperatorForGroup: " +  getStackTrace(e));
        }
        String role1 = "upload_onc_history";
        int roleId1 = UserAdminService.getRoleId(role1);
        Assert.assertTrue(roleId1 > 0);
        String role2 = "upload_ror_file";
        int roleId2 = UserAdminService.getRoleId(role2);
        Assert.assertTrue(roleId2 > 0);
        List<String> roles = List.of(role1, role2);

        String user1 = "testUser2@study.org";
        int userId1 = createTestUser(user1, roleId1);
        addRoleForUser(roleId2, userId1);
        String user2 = "testUser3@study.org";
        int userId2 = createTestUser(user2, roleId1);
        addRoleForUser(roleId2, userId2);

        List<String> users = List.of(user1, user2);

        UserRoleRequest req = new UserRoleRequest(users, roles);

        UserAdminService service = new UserAdminService(Integer.toString(operatorId), TEST_GROUP);
        try {
            service.addUserToRoles(req);
            Assert.fail("UserAdminService.addUserToRoles should fail with roles not in study");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("Invalid user"));
        }

        int groupRoleId1 = addGroupRole(roleId1, userAdminRoleId);
        int groupRoleId2 = addGroupRole(roleId2, userAdminRoleId);
        try {
            service.addUserToRoles(req);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.addUserToRole: " +  getStackTrace(e));
        }
        int userRoleId = UserAdminService.getUserRole(userId1, roleId1, groupId);
        Assert.assertNotEquals(-1, userRoleId);
        int userRoleId2 = UserAdminService.getUserRole(userId1, roleId2, groupId);
        Assert.assertNotEquals(-1, userRoleId2);

        int user2RoleId = UserAdminService.getUserRole(userId2, roleId1, groupId);
        Assert.assertNotEquals(-1, user2RoleId);
        int user2RoleId2 = UserAdminService.getUserRole(userId2, roleId2, groupId);
        Assert.assertNotEquals(-1, user2RoleId2);

        UserRoleRequest req2 = new UserRoleRequest(users, List.of(role1));
        try {
            service.removeUserFromRoles(req2);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.removeUserFromRoles: " +  getStackTrace(e));
        }

        UserRequest getReq = new UserRequest(users);
        UserRoleResponse res = null;
        try {
            res = service.getUserRoles(getReq);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.getUserRoles: " +  getStackTrace(e));
        }

        List<UserInfo> userInfoList = res.getUsers();
        Map<String, List<String>> userRoles = getUserRoles(userInfoList);
        for (var resRoles: userRoles.entrySet()) {
            Assert.assertTrue("RemoveUserFromRole removed wrong role", resRoles.getValue().contains(role2));
            Assert.assertFalse("RemoveUserFromRole did not remove role", resRoles.getValue().contains(role1));
        }

        // TODO: assert that result also has unassigned roles

        // adjust cleanup
        removeRoleForUser(roleId1, userId1);
        removeRoleForUser(roleId1, userId2);

        UserRoleRequest req3 = new UserRoleRequest(List.of(user2), List.of(role2));
        try {
            service.removeUserFromRoles(req3);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.removeUserFromRoles: " +  getStackTrace(e));
        }

        UserRequest getReq2 = new UserRequest(users);
        try {
            res = service.getUserRoles(getReq2);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.getUserRoles: " +  getStackTrace(e));
        }

        userInfoList = res.getUsers();
        for (var userInfo: userInfoList) {
            List<UserRole> resRoles = userInfo.getRoles();
            if (userInfo.getEmail().equals(user1)) {
                Assert.assertEquals(2, resRoles.size());

                List<UserRole> hasRole = resRoles.stream().filter(UserRole::isHasRole).collect(Collectors.toList());
                Assert.assertEquals(1, hasRole.size());

                List<UserRole> filteredRoles = resRoles.stream().filter(r -> r.getName().equals(role2)).collect(Collectors.toList());
                Assert.assertEquals(1, filteredRoles.size());
                Assert.assertTrue(filteredRoles.get(0).isHasRole());
            } else {
                Assert.assertEquals(2, resRoles.size());

                List<UserRole> hasRole = resRoles.stream().filter(UserRole::isHasRole).collect(Collectors.toList());
                Assert.assertTrue("RemoveUserFromRole did not remove role", hasRole.isEmpty());
            }
        }

        // adjust cleanup
        removeRoleForUser(roleId2, userId2);
    }

    @Test
    public void testAddAndRemoveUser() {
        int operatorId = createAdminUser("test_admin3@study.org", userAdminRoleId);
        int groupId = UserAdminService.verifyStudyGroup(TEST_GROUP);
        try {
            UserAdminService.verifyOperatorGroup(operatorId, groupId);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.verifyOperatorForGroup: " +  getStackTrace(e));
        }

        String email = "testUser4@study.org";
        AddUserRequest req = new AddUserRequest("testUser4", email, null, null);

        UserAdminService service = new UserAdminService(Integer.toString(operatorId), TEST_GROUP);
        try {
            service.createUser(req);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.createUser: " +  getStackTrace(e));
        }

        int userId = -1;
        try {
            userId = UserAdminService.getUserByEmail(email, -1);
        } catch (Exception e) {
            Assert.fail("Exception verifying UserAdminService.createUser: " +  getStackTrace(e));
        }

        int roleId = UserAdminService.getRoleId("upload_onc_history");
        Assert.assertTrue(roleId > 0);

        try {
            UserAdminService.addUserRole(userId, roleId, groupId);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.addUserRole: " +  getStackTrace(e));
        }

        try {
            service.removeUser(req);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.removeUser: " +  getStackTrace(e));
        }

        try {
            UserAdminService.getUserByEmail(email, -1);
            Assert.fail("UserAdminService.removeUser failed to remove user");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("Invalid user"));
        }
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
            log.info("TEMP: createAdminUser: userId={}, adminRoleId={}", userId, adminRoleId);
            addUserRole(userId, adminRoleId, groupId);
            log.info("Created admin user wih id {}", userId);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.addUserRole: " +  getStackTrace(e));
        }
        return userId;
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
            int userId = UserAdminService.getUserByEmail(email, -1);
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

    private Map<String, List<String>> getUserRoles(List<UserInfo> userInfoList) {
        Map<String, List<String>> userRoles = new HashMap<>();
        for (UserInfo userInfo: userInfoList) {
            List<String> roles = userInfo.getRoles().stream().filter(UserRole::isHasRole)
                    .map(UserRole::getName).collect(Collectors.toList());
            userRoles.put(userInfo.getEmail(), roles);
        }
        return userRoles;
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
