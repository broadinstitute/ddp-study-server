package org.broadinstitute.dsm.service.admin;

import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.DbTxnBaseTest;
import org.broadinstitute.dsm.db.dao.user.UserDao;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

@Slf4j
public class UserAdminServiceTest extends DbTxnBaseTest {

    private static final Map<Integer, List<Integer>> createdUserRoles = new HashMap<>();
    private static final String TEST_GROUP = "test_group";
    private static int studyGroupId;

    @BeforeClass
    public static void setup() {
        studyGroupId = UserAdminService.addStudyGroup(TEST_GROUP);
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
        String role = "upload_onc_history";
        int roleId = UserAdminService.verifyRole(role, -1);
        Assert.assertTrue(roleId > 0);
        String role2 = "upload_ror_file";
        int roleId2 = UserAdminService.verifyRole(role2, -1);
        Assert.assertTrue(roleId2 > 0);
        List<String> roles = List.of(role, role2);

        String user1 = "testUser2@study.org";
        int userId1 = createTestUser(user1, roleId);
        addRoleForUser(roleId2, userId1);
        String user2 = "testUser3@study.org";
        int userId2 = createTestUser(user2, roleId);
        addRoleForUser(roleId2, userId2);

        List<String> users = List.of(user1, user2);

        int groupId = UserAdminService.verifyStudyGroup(TEST_GROUP);
        UserRoleRequest req = new UserRoleRequest(users, TEST_GROUP, roles);

        UserAdminService service = new UserAdminService(Integer.toString(operatorId));
        try {
            service.addUserToRoles(req);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.addUserToRole: " +  getStackTrace(e));
        }
        int userRoleId = UserAdminService.getUserRole(userId1, roleId, groupId);
        Assert.assertNotEquals(-1, userRoleId);
        int userRoleId2 = UserAdminService.getUserRole(userId1, roleId2, groupId);
        Assert.assertNotEquals(-1, userRoleId2);

        int user2RoleId = UserAdminService.getUserRole(userId2, roleId, groupId);
        Assert.assertNotEquals(-1, user2RoleId);
        int user2RoleId2 = UserAdminService.getUserRole(userId2, roleId2, groupId);
        Assert.assertNotEquals(-1, user2RoleId2);
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
        UserRequest req = new UserRequest("testUser4", email, null, TEST_GROUP);

        UserAdminService service = new UserAdminService(Integer.toString(operatorId));
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
}
