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
                UserAdminService.deleteUserRole(userId, userRole);
            }
            userDao.delete(userId);
        }
        UserAdminService.deleteStudyGroup(studyGroupId);
    }

    private int createUser(String email, int roleId) {
        String name = email.split("@")[0];
        UserDto userDto = new UserDto();
        userDto.setName(name);
        userDto.setEmail(email);
        UserDao userDao = new UserDao();
        int userId = userDao.create(userDto);
        List<Integer> roleIds = new ArrayList<>();
        if (roleId != -1) {
            roleIds.add(roleId);
        }
        createdUserRoles.put(userId, roleIds);
        return userId;
    }

    private int addUserRole(int userId, int roleId, int groupId) throws Exception {
        int userRoleId = UserAdminService.addUserRole(userId, roleId, groupId);
        List<Integer> roleIds = createdUserRoles.get(userId);
        List<Integer> newRoleIds = new ArrayList<>(roleIds);
        newRoleIds.add(roleId);
        createdUserRoles.put(userId, newRoleIds);
        return userRoleId;
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
        int userId = createUser("test_admin1@study.org", roleId);
        int groupId = UserAdminService.verifyStudyGroup(TEST_GROUP);
        try {
            UserAdminService.verifyOperatorForGroup(Integer.toString(userId), TEST_GROUP);
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
            UserAdminService.verifyOperatorForGroup(Integer.toString(userId), TEST_GROUP);
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
            UserAdminService.verifyOperatorForGroup(Integer.toString(userId), TEST_GROUP);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.verifyOperatorForGroup: " +  getStackTrace(e));
        }
    }

    @Test
    public void testGetUserByEmailAndGroup() {
        int roleId = UserAdminService.verifyRole("upload_onc_history", -1);
        Assert.assertTrue(roleId > 0);
        String email = "testUser@study.org";
        int userId = createUser(email, -1);
        int groupId = UserAdminService.verifyStudyGroup(TEST_GROUP);
        try {
            int id = UserAdminService.getUserByEmail(email, groupId);
            Assert.assertEquals(userId, id);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.getUserByEmail: " +  getStackTrace(e));
        }
    }

    @Test
    public void testAddUserToRole() {
        int operatorId = createAdminUser("test_admin2@study.org");
        String role = "upload_onc_history";
        int roleId = UserAdminService.verifyRole(role, -1);
        Assert.assertTrue(roleId > 0);
        String email = "testUser2@study.org";
        int userId = createUser(email, roleId);
        int groupId = UserAdminService.verifyStudyGroup(TEST_GROUP);
        AddUserRoleRequest req = new AddUserRoleRequest(email, role, TEST_GROUP);

        UserAdminService service = new UserAdminService(Integer.toString(operatorId));
        try {
            service.addUserToRole(req);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.addUserToRole: " +  getStackTrace(e));
        }
        int userRoleId = UserAdminService.getUserRole(userId, roleId, groupId);
        Assert.assertNotEquals(-1, userRoleId);
    }

    private int createAdminUser(String email) {
        int adminRoleId = UserAdminService.verifyRole("study_admin", -1);
        int userId = createUser(email, adminRoleId);
        int groupId = UserAdminService.verifyStudyGroup(TEST_GROUP);
        try {
            return addUserRole(userId, adminRoleId, groupId);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.addUserRole: " +  getStackTrace(e));
        }
        return -1;
    }
}
