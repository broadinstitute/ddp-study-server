package org.broadinstitute.dsm.service.admin;

import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;
import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private static final String TEST_GROUP = "test_group";
    private static final String TEST_INSTANCE = "test_instance";
    private static int studyGroupId;
    private static int ddpInstanceId;

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

    @Test
    public void testVerifyRole() {
        int roleId = UserAdminService.verifyRole("upload_onc_history", -1);
        Assert.assertTrue(roleId > 0);
    }

    @Test
    public void testVerifyOperatorForGroup() {
        int roleId = UserAdminService.verifyRole("upload_onc_history", -1);
        Assert.assertTrue(roleId > 0);
        int userId = createTestUser("test_admin1@study.org", roleId);
        int groupId = UserAdminService.verifyStudyGroup(TEST_GROUP);
        try {
            UserAdminService.verifyOperatorForGroup(userId, TEST_GROUP);
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
            UserAdminService.verifyOperatorForGroup(userId, TEST_GROUP);
            Assert.fail("Expecting exception from UserAdminService.verifyOperatorForGroup");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("does not have administrator privileges"));
        }

        int adminRoleId = UserAdminService.verifyRole("study_admin", -1);
        try {
            addUserRole(userId, adminRoleId, groupId);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.addUserRole: " +  getStackTrace(e));
        }
        try {
            UserAdminService.verifyOperatorForGroup(userId, TEST_GROUP);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.verifyOperatorForGroup: " +  getStackTrace(e));
        }
    }

    @Test
    public void testGetUserByEmailAndGroup() {
        int roleId = UserAdminService.verifyRole("upload_onc_history", -1);
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
    public void testAddUserToRoles() {
        int operatorId = createAdminUser("test_admin2@study.org");
        try {
            UserAdminService.verifyOperatorForGroup(operatorId, TEST_GROUP);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.verifyOperatorForGroup: " +  getStackTrace(e));
        }
        String role1 = "upload_onc_history";
        int roleId1 = UserAdminService.verifyRole(role1, -1);
        Assert.assertTrue(roleId1 > 0);
        String role2 = "upload_ror_file";
        int roleId2 = UserAdminService.verifyRole(role2, -1);
        Assert.assertTrue(roleId2 > 0);
        List<String> roles = List.of(role1, role2);

        String user1 = "testUser2@study.org";
        int userId1 = createTestUser(user1, roleId1);
        addRoleForUser(roleId2, userId1);
        String user2 = "testUser3@study.org";
        int userId2 = createTestUser(user2, roleId1);
        addRoleForUser(roleId2, userId2);

        List<String> users = List.of(user1, user2);

        int groupId = UserAdminService.verifyStudyGroup(TEST_GROUP);
        UserRoleRequest req = new UserRoleRequest(users, roles);

        UserAdminService service = new UserAdminService(Integer.toString(operatorId), TEST_GROUP);
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

        UserRoleRequest getReq = new UserRoleRequest(users, null);
        UserRoleResponse res = null;
        try {
            res = service.getUserRoles(getReq);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.getUserRoles: " +  getStackTrace(e));
        }

        List<UserRoleResponse.UserRoles> userRoles = res.getUserRoles();
        for (UserRoleResponse.UserRoles resRoles: userRoles) {
            Assert.assertTrue("RemoveUserFromRole removed wrong role", resRoles.roles.contains(role2));
            Assert.assertFalse("RemoveUserFromRole did not remove role", resRoles.roles.contains(role1));
        }

        // adjust cleanup
        removeRoleForUser(roleId1, userId1);
        removeRoleForUser(roleId1, userId2);

        UserRoleRequest req3 = new UserRoleRequest(List.of(user2), List.of(role2));
        try {
            service.removeUserFromRoles(req3);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.removeUserFromRoles: " +  getStackTrace(e));
        }

        UserRoleRequest getReq2 = new UserRoleRequest(users, null);
        try {
            res = service.getUserRoles(getReq2);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.getUserRoles: " +  getStackTrace(e));
        }

        userRoles = res.getUserRoles();
        for (UserRoleResponse.UserRoles resRoles: userRoles) {
            if (resRoles.user.equals(user1)) {
                Assert.assertTrue(resRoles.roles.contains(role2));
            } else {
                Assert.assertTrue("RemoveUserFromRole did not remove role", resRoles.roles.isEmpty());
            }
        }

        // adjust cleanup
        removeRoleForUser(roleId2, userId2);
    }

    @Test
    public void testAddAndRemoveUser() {
        int operatorId = createAdminUser("test_admin3@study.org");
        int groupId = -1;
        try {
            groupId = UserAdminService.verifyOperatorForGroup(operatorId, TEST_GROUP);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.verifyOperatorForGroup: " +  getStackTrace(e));
        }

        String email = "testUser4@study.org";
        UserRequest req = new UserRequest("testUser4", email, null);

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

        int roleId = UserAdminService.verifyRole("upload_onc_history", -1);
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

    private int createAdminUser(String email) {
        int adminRoleId = UserAdminService.verifyRole("study_admin", -1);
        int userId = createTestUser(email, adminRoleId);
        int groupId = UserAdminService.verifyStudyGroup(TEST_GROUP);
        try {
            addUserRole(userId, adminRoleId, groupId);
            log.info("Created admin user wih id {}", userId);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.addUserRole: " +  getStackTrace(e));
        }
        return userId;
    }
    
    private void setUserRoles(String email, List<String> roles, String studyGroup) throws Exception {
        try {
            int userId = UserAdminService.getUserByEmail(email, -1);
            int groupId = UserAdminService.verifyStudyGroup(studyGroup);

            for (String role : roles) {
                int roleId = UserAdminService.verifyRole(role, -1);
                UserAdminService.addUserRole(userId, roleId, groupId);
                String msg = String.format("Set up role %s for user %s in study group %s", role, email, studyGroup);
                log.info(msg);
            }
        } catch (Exception e) {
            log.error("Exception in setUserRoles: " +  getStackTrace(e));
            throw e;
        }
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
