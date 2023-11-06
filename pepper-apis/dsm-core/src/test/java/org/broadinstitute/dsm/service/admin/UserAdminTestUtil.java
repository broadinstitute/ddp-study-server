package org.broadinstitute.dsm.service.admin;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.dao.user.UserDao;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.util.DdpInstanceGroupTestUtil;
import org.junit.Assert;

/**
 * Provides utility methods to help set up and tear down test realms, test study roles, test users and their roles.
 * This class keeps track of the entities it creates and will tear them down as requested and/or all at once
 * when the close() method is called.
 * If you create users and roles directly with the UserAdminService, you need to manage their lifecycle.
 * <p></p>
 * The basic usage is:
 * Setup infrastructure (typically in a @BeforeClass or @Before method), but only once per class instance
 * Create an instance of this class and call createRealmAndStudyGroup()
 * Call setStudyAdminAndRoles() to set up a study user admin and study roles (these include all user roles you plan to assign)
 * Note: This class expects actual DSM roles.
 * <p></p>
 * Create users and roles (typically in test)
 * Via UserAdminService if you manage the user and role lifecycle
 * Or call createTestUser() and methods to add and remove user roles, which are managed by this class
 * <p></p>
 * Delete users and roles (typically in a test or in an @After method)
 * Via UserAdminService if you manage the user and role lifecycle
 * Or all deleteTestUser() for users managed by this class
 * <p></p>
 * Delete all entities created by this class (typically in an @After or @AfterClass method)
 * Call close()
 */
@Slf4j
public class UserAdminTestUtil {
    private final Map<Integer, List<Integer>> createdUserRoles = new HashMap<>();
    private final List<Integer> createdGroupRoles = new ArrayList<>();
    private int studyGroupId = -1;
    private int ddpInstanceId = -1;
    private int userAdminRoleId;
    private int userAdminId = -1;
    private Map<String, Integer> allRoles;

    private boolean initialized = false;


    public UserAdminTestUtil() {
    }

    protected static Map<String, Integer> getAllRoles() {
        return inTransaction(conn -> {
            Map<String, Integer> roles = new HashMap<>();
            try (PreparedStatement stmt = conn.prepareStatement("SELECT ar.role_id, ar.name FROM access_role ar")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        roles.put(rs.getString(2), rs.getInt(1));
                    }
                }
            } catch (SQLException e) {
                throw new DsmInternalError("Error getting roles", e);
            }
            return roles;
        });
    }

    /**
     * Call this to teardown this class, typically in an @After or @AfterClass method
     */
    public void deleteGeneratedData() {
        deleteStudyAdminAndRoles();
        DdpInstanceGroupTestUtil.deleteInstanceGroup(ddpInstanceId);
        ddpInstanceId = -1;
        DdpInstanceGroupTestUtil.deleteStudyGroup(studyGroupId);
        studyGroupId = -1;
    }

    private void initialize() {
        if (!initialized) {
            allRoles = getAllRoles();
            initialized = true;
        }
    }

    private void assertInitialized() {
        if (!initialized) {
            throw new DsmInternalError("Test users not initialized: call createRealmAndStudyGroup");
        }
    }

    /**
     * Checks if the instance is already initialized, if not
     * initialize class and create a test DDP realm/instance and study group
     *
     * @param realmName          name of study realm that is not already in use
     * @param studyGuid          study guid of the new realm we are creating
     * @param collaboratorPrefix string that appears before the collaborator sample and participant ids specific to this realm
     * @param studyGroup         name of study group that is not already in use to associate with realm
     * @param esIndex           ES index
     */
    public void createRealmAndStudyGroup(@NonNull String realmName, String studyGuid, String collaboratorPrefix, String studyGroup,
                                         String esIndex) {
        ddpInstanceId = DdpInstanceGroupTestUtil.createInstance(realmName, studyGuid, collaboratorPrefix, esIndex).getDdpInstanceId();
        studyGroupId = DdpInstanceGroupTestUtil.createGroup(studyGroup);

        initialize();
        DdpInstanceGroupTestUtil.createInstanceGroup(realmName, studyGroup);
    }

    /**
     * Setup study admin and study roles
     *
     * @param adminEmail    for new admin user account
     * @param adminRole     PEPPER_ADMIN or STUDY_USER_ADMIN
     * @param rolesToManage actual DSM roles
     * @return study admin user ID
     */
    public int setStudyAdminAndRoles(String adminEmail, String adminRole, List<String> rolesToManage) {
        assertInitialized();
        if (userAdminId != -1) {
            throw new DsmInternalError("setStudyAdminAndRoles already initialized");
        }
        userAdminRoleId = UserAdminService.getRoleId(adminRole);
        userAdminId = createUser(adminEmail);
        UserAdminService.addUserRole(userAdminId, userAdminRoleId, studyGroupId);

        Map<String, UserAdminService.RoleInfo> adminRoles = UserAdminService.getOperatorAdminRoles(userAdminId, studyGroupId);
        Assert.assertTrue("adminRoles = " + adminRoles.keySet(), adminRoles.isEmpty());

        if (rolesToManage != null) {
            addStudyRoles(rolesToManage);
        }
        return userAdminId;
    }

    /**
     * Add roles for study
     *
     * @param roles actual DSM roles
     */
    public void addStudyRoles(List<String> roles) {
        assertInitialized();
        for (String role : roles) {
            addGroupRole(getRoleId(role), userAdminRoleId);
        }
    }

    private int addGroupRole(int roleId, int adminRoleId) {
        int groupRoleId = UserAdminService.addGroupRole(studyGroupId, roleId, adminRoleId);
        createdGroupRoles.add(groupRoleId);
        return groupRoleId;
    }

    /**
     * Remove roles for study
     *
     * @param roles existing study roles
     */
    public void removeStudyRoles(List<String> roles) {
        assertInitialized();
        for (String role : roles) {
            removeGroupRole(getRoleId(role));
        }
    }

    private void removeGroupRole(int groupRoleId) {
        UserAdminService.deleteGroupRole(groupRoleId);
        createdGroupRoles.remove(groupRoleId);
    }

    /**
     * Delete all study users, study admin and study roles (typically called from an @After or @AfterClass method)
     */
    public void deleteStudyAdminAndRoles() {
        assertInitialized();
        deleteAllTestUsers();

        UserAdminService.deleteUserRole(userAdminId, userAdminRoleId, studyGroupId);
        new UserDao().delete(userAdminId);
        userAdminId = -1;

        for (int groupRoleId : createdGroupRoles) {
            UserAdminService.deleteGroupRole(groupRoleId);
        }
    }

    /**
     * Create a test user
     *
     * @param roles list of actual DSM roles for user
     * @return user ID
     */
    public int createTestUser(String email, List<String> roles) {
        assertInitialized();
        int userId = createUser(email);
        List<Integer> roleIds = new ArrayList<>();
        for (String role : roles) {
            int roleId = getRoleId(role);
            UserAdminService.addUserRole(userId, roleId, studyGroupId);
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

    /**
     * Delete test user and their roles
     */
    public void deleteTestUser(int userId) {
        assertInitialized();
        _deleteTestUser(userId, new UserDao());
        createdUserRoles.remove(userId);
    }

    private void _deleteTestUser(int userId, UserDao userDao) {
        List<Integer> roleIds = createdUserRoles.get(userId);
        if (roleIds == null) {
            throw new DsmInternalError("Invalid user: " + userId);
        }
        for (int roleId : roleIds) {
            UserAdminService.deleteUserRole(userId, roleId, studyGroupId);
        }
        userDao.delete(userId);
    }

    public void deleteAllTestUsers() {
        assertInitialized();
        UserDao userDao = new UserDao();
        for (var entry : createdUserRoles.entrySet()) {
            _deleteTestUser(entry.getKey(), userDao);
        }
        createdUserRoles.clear();
    }

    /**
     * Add a roles for user
     *
     * @param roles actual DSM roles
     */
    public void addUserRoles(int userId, List<String> roles) {
        assertInitialized();
        for (String role : roles) {
            int roleId = getRoleId(role);
            UserAdminService.addUserRole(userId, roleId, studyGroupId);
            addRoleForUser(roleId, userId);
        }
    }

    protected void addRoleForUser(int roleId, int userId) {
        List<Integer> roleIds = createdUserRoles.get(userId);
        if (roleIds == null) {
            throw new DsmInternalError("Invalid user: " + userId);
        }
        List<Integer> newRoleIds = new ArrayList<>(roleIds);
        newRoleIds.add(roleId);
        createdUserRoles.put(userId, newRoleIds);
    }

    protected void addRoleForUser(String role, int userId) {
        addRoleForUser(getRoleId(role), userId);
    }

    /**
     * Remove role for user
     *
     * @param role existing user role
     */
    public void removeUserRole(int userId, String role) {
        assertInitialized();
        int roleId = getRoleId(role);
        UserAdminService.deleteUserRole(userId, roleId, studyGroupId);
        removeRoleForUser(roleId, userId);
    }

    protected void removeRoleForUser(int roleId, int userId) {
        List<Integer> roleIds = createdUserRoles.get(userId);
        if (roleIds == null) {
            throw new DsmInternalError("Invalid user: " + userId);
        }
        List<Integer> newRoleIds = new ArrayList<>(roleIds);
        newRoleIds.removeAll(List.of(roleId));
        createdUserRoles.put(userId, newRoleIds);
    }

    protected void removeRoleForUser(String role, int userId) {
        removeRoleForUser(getRoleId(role), userId);
    }

    public void verifyUserHasRole(int userId, String role) {
        assertInitialized();
        int userRoleId = UserAdminService.getUserRole(userId, getRoleId(role), studyGroupId);
        Assert.assertNotEquals(-1, userRoleId);
    }

    public int getRoleId(String roleName) {
        assertInitialized();
        Integer roleId = allRoles.get(roleName);
        if (roleId == null) {
            throw new DsmInternalError("Invalid role name: " + roleName);
        }
        return roleId;
    }

    public int getStudyGroupId() {
        return studyGroupId;
    }

    public int getDdpInstanceId() {
        return ddpInstanceId;
    }


}
