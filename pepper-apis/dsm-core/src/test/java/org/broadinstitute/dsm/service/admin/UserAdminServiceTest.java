package org.broadinstitute.dsm.service.admin;

import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;
import static org.broadinstitute.dsm.service.admin.UserAdminService.PEPPER_ADMIN_ROLE;
import static org.broadinstitute.dsm.service.admin.UserAdminService.USER_ADMIN_ROLE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.DbTxnBaseTest;
import org.broadinstitute.dsm.db.UserSettings;
import org.broadinstitute.dsm.db.dao.user.UserDao;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.dsm.statics.RoutePath;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

@Slf4j
public class UserAdminServiceTest extends DbTxnBaseTest {

    private static final String TEST_GROUP = "test_group";
    private static final String TEST_INSTANCE = "test_instance";
    private static String userAdminRole;
    private static UserAdminTestUtil testUsers;

    @BeforeClass
    public static void setup() {
        testUsers = new UserAdminTestUtil();
        testUsers.createRealmAndStudyGroup(TEST_INSTANCE, TEST_GROUP);
        userAdminRole = USER_ADMIN_ROLE;
        Assert.assertNotEquals(-1, UserAdminService.getRoleId(PEPPER_ADMIN_ROLE));
        Assert.assertNotEquals(-1, UserAdminService.getRoleId(USER_ADMIN_ROLE));
    }

    @AfterClass
    public static void tearDown() {
        testUsers.close();
    }

    @After
    public void cleanup() {
        testUsers.deleteStudyAdminAndRoles();
    }


    public void testGetRoleId() {
        // temporary to understand which roles are handled by liquibase
        log.info("TEMP: all roles: {}", String.join("\n", UserAdminTestUtil.getAllRoles().keySet()));
        int roleId = UserAdminService.getRoleId("upload_onc_history");
        Assert.assertTrue(roleId > 0);
    }

    @Test
    public void testVerifyOperatorGroup() {
        String roleName = "dashboard_view";
        int userId = -1;
        try {
            userId = testUsers.createTestUser("test_admin1@study.org", List.of(roleName));
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail();
        }
        int groupId = UserAdminService.verifyStudyGroup(TEST_GROUP);

        UserAdminService service = new UserAdminService(Integer.toString(userId), TEST_GROUP);
        try {
            service.verifyOperatorAdminRoles(userId, groupId);
            Assert.fail("Expecting exception from UserAdminService.verifyOperatorForGroup");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("does not have user administrator privileges"));
        }

        try {
            testUsers.addUserRoles(userId, List.of(roleName));
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
            testUsers.addUserRoles(userId, List.of(userAdminRole));
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.addUserRole: " +  getStackTrace(e));
        }

        testUsers.addStudyRoles(List.of(roleName));

        try {
            service.verifyOperatorAdminRoles(userId, groupId);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.verifyOperatorForGroup: " +  getStackTrace(e));
        }
    }

    @Test
    public void testGetUserByEmailAndGroup() {
        String roleName = "upload_onc_history";
        String email = "test_user1@study.org";
        int userId = -1;
        try {
            userId = testUsers.createTestUser(email, List.of(roleName));
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail();
        }
        int groupId = UserAdminService.verifyStudyGroup(TEST_GROUP);
        try {
            Assert.assertEquals(userId, UserAdminService.verifyUserByEmail(email, groupId).getId());
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
            Assert.assertTrue(e.getMessage().contains("must have at least one role"));
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
    public void testSetUserRoles() {
        String role1 = "upload_onc_history";
        String role1Display = "Onc history: Upload";
        String role2 = "upload_ror_file";
        List<String> roles = List.of(role1, role2);
        String user1 = "test_user2@study.org";
        String user2 = "test_user3@study.org";
        List<String> users = List.of(user1, user2);
        Map<String, Integer> usersToId = setupUsers(users, roles);

        // let operator manage one of the roles
        int operatorId = testUsers.setStudyAdminAndRoles("test_admin2@study.org", userAdminRole, List.of(role1));

        UserAdminService service = new UserAdminService(Integer.toString(operatorId), TEST_GROUP);

        SetUserRoleRequest req = new SetUserRoleRequest(users, roles);
        try {
            service.setUserRoles(req);
            Assert.fail("UserAdminService.setUserRoles should fail with roles not in study");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("Invalid roles for study group"));
        }

        // both roles are now in the study
        testUsers.addStudyRoles(List.of(role2));

        // verify by getting all study roles
        Set<String> allRoles = Set.of(role1, role2);
        StudyRoleResponse srRes = service.getStudyRoles();
        Assert.assertEquals(srRes.getRoles().stream().map(StudyRoleResponse.Role::getName).collect(Collectors.toSet()), allRoles);

        // idempotent
        try {
            service.setUserRoles(req);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.setUserRoles: " +  getStackTrace(e));
        }

        // verify internally
        for (var userId: usersToId.values()) {
            for (String role: roles) {
                testUsers.verifyUserHasRole(userId, role);
            }
        }

        // get roles and verify
        UserRoleRequest getReq = new UserRoleRequest(users);
        UserRoleResponse res = null;
        try {
            res = service.getUserRoles(getReq);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.getUserRoles: " +  getStackTrace(e));
        }

        List<UserInfo> userInfoList = res.getUsers();
        Map<String, Map<String, Boolean>> userRoles = getUserRoles(userInfoList);
        for (var userInfo: userInfoList) {
            UserRole userRole = userInfo.getRoles().stream().filter(r ->
                    r.getName().equals(role1)).collect(Collectors.toList()).get(0);
            Assert.assertEquals(role1Display, userRole.getDisplayText());
        }
        verifyResponseRoles(userRoles.get(user1), List.of(role1, role2), List.of(role1, role2));
        verifyResponseRoles(userRoles.get(user2), List.of(role1, role2), List.of(role1, role2));

        // remove one role for both users
        SetUserRoleRequest req2 = new SetUserRoleRequest(users, List.of(role2));

        // user has old role that is not in the study
        String badRole = "study_admin";
        //setUserRoles(usersToId.get(user1), List.of(badRole), groupId);
        testUsers.addUserRoles(usersToId.get(user1), List.of(badRole));

        try {
            service.setUserRoles(req2);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.setUserRoles: " +  getStackTrace(e));
        } finally {
            testUsers.addRoleForUser(UserAdminService.getRoleId(badRole), usersToId.get(user1));
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
        testUsers.removeRoleForUser(role1, usersToId.get(user1));
        testUsers.removeRoleForUser(role1, usersToId.get(user2));

        // set no roles for one user, which should not be allowed
        SetUserRoleRequest req3 = new SetUserRoleRequest(List.of(user2), new ArrayList<>());
        try {
            service.setUserRoles(req3);
            Assert.fail("UserAdminService.setUserRoles should fail to remove all user roles");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("Invalid roles for study group"));
        }
    }

    @Test
    public void testUpdateUserRoles() {
        String role1 = "upload_onc_history";
        String role2 = "upload_ror_file";
        List<String> roles = List.of(role1, role2);
        String user1 = "test_user4@study.org";
        String user2 = "test_user5@study.org";
        List<String> users = List.of(user1, user2);
        Map<String, Integer> usersToId = setupUsers(users, roles);

        // let operator manage one of the roles
        int operatorId = testUsers.setStudyAdminAndRoles("test_admin3@study.org", userAdminRole, List.of(role1));

        UserAdminService service = new UserAdminService(Integer.toString(operatorId), TEST_GROUP);

        UpdateUserRoleRequest req = new UpdateUserRoleRequest(users, roles, new ArrayList<>());
        try {
            service.updateUserRoles(req);
            Assert.fail("UserAdminService.updateUserRoles should fail with roles not in study");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("Invalid roles for study group"));
        }

        // both roles are now in the study
        testUsers.addStudyRoles(List.of(role2));

        // verify by getting all study roles
        Set<String> allRoles = Set.of(role1, role2);
        StudyRoleResponse srRes = service.getStudyRoles();
        Assert.assertEquals(srRes.getRoles().stream().map(StudyRoleResponse.Role::getName).collect(Collectors.toSet()), allRoles);

        // idempotent
        try {
            service.updateUserRoles(req);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.updateUserRoles: " +  getStackTrace(e));
        }

        // verify internally
        for (var userId: usersToId.values()) {
            for (String role: roles) {
                testUsers.verifyUserHasRole(userId, role);
            }
        }

        // get roles and verify
        UserRoleRequest getReq = new UserRoleRequest(users);
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
        UpdateUserRoleRequest req2 = new UpdateUserRoleRequest(users, null, List.of(role1));
        try {
            service.updateUserRoles(req2);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.updateUserRoles: " +  getStackTrace(e));
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
        testUsers.removeRoleForUser(role1, usersToId.get(user1));
        testUsers.removeRoleForUser(role1, usersToId.get(user2));

        // remove last role for one user, which should not be allowed

        // user has old role that is not in the study
        String badRole = "study_admin";
        testUsers.addUserRoles(usersToId.get(user2), List.of(badRole));

        UpdateUserRoleRequest req3 = new UpdateUserRoleRequest(List.of(user2), null, List.of(role2));
        try {
            service.updateUserRoles(req3);
            Assert.fail("UserAdminService.updateUserRoles should fail to remove all user roles");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("Cannot remove all roles for user"));
        } finally {
            testUsers.addRoleForUser(UserAdminService.getRoleId(badRole), usersToId.get(user2));
        }
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

        int operatorId = testUsers.setStudyAdminAndRoles("test_admin4@study.org", userAdminRole, roles);

        String user = "test_user6@study.org";
        String userVariation = "TESt_user6@study.org";
        String userName = "test_user6";

        UserAdminService service = new UserAdminService(Integer.toString(operatorId), TEST_GROUP);
        UserRequest badUserRequest = new UserRequest(List.of(new UserRequest.User(user, userName, null,
                List.of("bad_role"))), null);
        try {
            service.addAndRemoveUsers(badUserRequest);
            Assert.fail("UserAdminService.addUser should fail with bad role names");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("Invalid roles for study group"));
        }

        UserRequest userRequest = new UserRequest(List.of(new UserRequest.User(user, userName, null,
                List.of(role1))), null);

        try {
            service.addAndRemoveUsers(userRequest);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.addAndRemoveUsers: " +  getStackTrace(e));
        }

        // add a role
        UpdateUserRoleRequest roleReq = new UpdateUserRoleRequest(List.of(user), List.of(role2), null);
        try {
            service.updateUserRoles(roleReq);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.updateUserRoles: " +  getStackTrace(e));
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
        UpdateUserRequest.User updateUser = new UpdateUserRequest.User(userVariation, newUserName, phone);
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

        // user should have user settings
        UserSettings settings = UserSettings.getUserSettings(user);
        Assert.assertNotNull(settings);

        userInfoList = res.getUsers();
        verifyUserInfo(userInfoList, user, newUserName, phone);

        userRoles = getUserRoles(userInfoList);
        verifyResponseRoles(userRoles.get(user), List.of(role1, role2), List.of(role1, role2));

        UserRequest removeReq = new UserRequest(null, List.of(user));
        try {
            service.addAndRemoveUsers(removeReq);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.addAndRemoveUsers: " +  getStackTrace(e));
        }

        try {
            UserAdminService.verifyUserByEmail(user, groupId);
            Assert.fail("UserAdminService.addAndRemoveUsers failed to remove user");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("Invalid user"));
        }

        try {
            service.updateUser(updateReq);
            Assert.fail("UserAdminService.updateUser should fail to update a removed user");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("Invalid user for study group (inactive)"));
        }

        settings = UserSettings.getUserSettings(user);
        Assert.assertNull(settings);
    }


    @Test
    public void testAddExistingUser() {
        int groupId = UserAdminService.verifyStudyGroup(TEST_GROUP);

        String role1 = "upload_onc_history";
        String role2 = "upload_ror_file";
        List<String> roles = List.of(role1, role2);

        int operatorId = testUsers.setStudyAdminAndRoles("test_admin5@study.org", userAdminRole, roles);

        String user = "test_user7@study.org";
        String userVariation = "Test_User7@study.org";
        String userName = "test_user7";
        UserRequest addUserRequest = new UserRequest(List.of(new UserRequest.User(user, userName, null,
                roles)), null);

        UserAdminService service = new UserAdminService(Integer.toString(operatorId), TEST_GROUP);
        try {
            service.addAndRemoveUsers(addUserRequest);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.addAndRemoveUsers: " +  getStackTrace(e));
        }

        UserRequest removeReq = new UserRequest(null, List.of(user));
        try {
            service.addAndRemoveUsers(removeReq);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.addAndRemoveUsers: " +  getStackTrace(e));
        }

        // add user back to test the inactive to active transition
        try {
            service.addAndRemoveUsers(addUserRequest);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.addAndRemoveUsers: " +  getStackTrace(e));
        }

        // try to add again
        try {
            service.addAndRemoveUsers(addUserRequest);
            Assert.fail("UserAdminService.addAndRemoveUsers should fail to add an existing study user");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("Already has roles in study"));
        }

        // remove all user study roles
        int userId = UserAdminService.verifyUserByEmail(user, groupId).getId();
        for (String role: roles) {
            int count = UserAdminService.deleteUserRole(userId, testUsers.getRoleId(role), groupId);
            Assert.assertEquals(1, count);
        }
        Assert.assertTrue(UserAdminService.getRolesForUser(userId, groupId).isEmpty());

        // user with no roles in this study can be added
        try {
            service.addAndRemoveUsers(addUserRequest);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.addAndRemoveUsers: " +  getStackTrace(e));
        }

        // cleanup (with case variation on email address)
        UserRequest removeReq2 = new UserRequest(null, List.of(userVariation));
        try {
            service.addAndRemoveUsers(removeReq2);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.addAndRemoveUsers: " +  getStackTrace(e));
        }

        try {
            service.addAndRemoveUsers(removeReq2);
            Assert.fail("UserAdminService.addAndRemoveUsers should fail to a remove study user that does not exist");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("Invalid user for study group"));
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

    @Test
    public void testValidateEmailFormat() {
        try {
            UserAdminService.validateEmailFormat("joe@gmail.com");
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.validateEmailFormat: " +  e);
        }
        try {
            UserAdminService.validateEmailFormat("justjoe");
            Assert.fail("UserAdminService.validateEmailFormat should fail with bad email address: justjoe");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("Invalid email address format"));
        }
        try {
            UserAdminService.validateEmailFormat("joe@gmail");
            Assert.fail("UserAdminService.validateEmailFormat should fail with bad email address: joe@gmail");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("Invalid email address format"));
        }
    }

    private Map<String, Integer> setupUsers(List<String> users, List<String> roles) {
        Map<String, Integer> userToId = new HashMap<>();
        for (String user: users) {
            int userId = testUsers.createTestUser(user, Collections.emptyList());
            for (String role : roles) {
                testUsers.addRoleForUser(role, userId);
            }
            userToId.put(user, userId);
        }
        return userToId;
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
}
