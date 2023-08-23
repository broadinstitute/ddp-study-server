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
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.dao.user.UserDao;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.lddp.db.SimpleResult;
import org.junit.Assert;

/**
 * Provides utility methods to help set up and tear down test users
 */
@Slf4j
public class UserAdminTestUsers {
    private final Map<Integer, List<Integer>> createdUserRoles = new HashMap<>();
    private final List<Integer> createdGroupRoles = new ArrayList<>();
    private final String adminRole;
    private final String studyGroup;
    private int studyGroupId;
    private int ddpInstanceId = -1;
    private int userAdminRoleId;
    private Map<String, UserAdminService.RoleInfo> studyRoles;

    private boolean initialized = false;

    private static final String SQL_INSERT_DDP_INSTANCE =
            "INSERT INTO ddp_instance SET instance_name = ?, is_active = 1, auth0_token = 1, migrated_ddp = 0";

    private static final String SQL_INSERT_DDP_INSTANCE_GROUP =
            "INSERT INTO ddp_instance_group SET ddp_instance_id = ?, ddp_group_id = ?";

    private static final String SQL_DELETE_DDP_INSTANCE_GROUP =
            "DELETE FROM ddp_instance_group WHERE ddp_instance_id = ?";

    private static final String SQL_DELETE_DDP_INSTANCE =
            "DELETE FROM ddp_instance WHERE ddp_instance_id = ?";


    public UserAdminTestUsers(String studyGroup, String adminRole) {
        this.studyGroup = studyGroup;
        this.adminRole = adminRole;
    }

    public void close() {
        UserDao userDao = new UserDao();
        for (var entry: createdUserRoles.entrySet()) {
            int userId = entry.getKey();
            List<Integer> userRoles = entry.getValue();
            for (int roleId: userRoles) {
                UserAdminService.deleteUserRole(userId, roleId, studyGroupId);
            }
            userDao.delete(userId);
        }
        deleteInstance(ddpInstanceId);
        UserAdminService.deleteStudyGroup(studyGroupId);
    }

    private void initialize() {
        if (!initialized) {
            studyGroupId = UserAdminService.verifyStudyGroup(studyGroup);
            studyRoles = UserAdminService.getAllRolesForStudy(studyGroupId);
            userAdminRoleId = UserAdminService.getRoleId(adminRole);
            initialized = true;
        }
    }

    public int createTestUser(String email, List<String> roles) {
        initialize();
        int userId = createUser(email);
        List<Integer> roleIds = new ArrayList<>();
        for (String role: roles) {
            roleIds.add(getRoleId(role));
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

    public int addUserRole(int userId, String role) {
        initialize();
        int roleId = getRoleId(role);
        int userRoleId = UserAdminService.addUserRole(userId, roleId, studyGroupId);
        addRoleForUser(roleId, userId);
        return userRoleId;
    }

    private int getRoleId(String roleName) {
        UserAdminService.RoleInfo roleInfo = studyRoles.get(roleName);
        if (roleInfo == null) {
            throw new DsmInternalError("Invalid role name for study");
        }
        return roleInfo.getRoleId();
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

    private int createAdminUser(String email, int adminRoleId) {
        int userId = createTestUser(email, adminRoleId);
        int groupId = UserAdminService.verifyStudyGroup(studyGroup);
        try {
            addUserRole(userId, adminRoleId, groupId);
            log.info("Created admin user {} wih id {} and adminRoleId={}", email, userId, adminRoleId);
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.addUserRole: " +  getStackTrace(e));
        }
        return userId;
    }

    private int setupAdmin(String adminEmail, List<String> rolesToManage) {
        int operatorId = createAdminUser(adminEmail, userAdminRoleId);
        try {
            Map<String, UserAdminService.RoleInfo> adminRoles = UserAdminService.getOperatorAdminRoles(operatorId, studyGroupId);
            Assert.assertTrue("adminRoles = " + adminRoles.keySet(), adminRoles.isEmpty());
        } catch (Exception e) {
            Assert.fail("Exception from UserAdminService.verifyOperatorForGroup: " +  getStackTrace(e));
        }

        if (rolesToManage != null) {
            for (String role: rolesToManage) {
                addGroupRole(getRoleId(role), userAdminRoleId);
            }
        }
        return operatorId;
    }

    private Map<String, Integer> setupUsers(List<String> users, List<String> roles) {

        Map<String, Integer> userToId = new HashMap<>();
        for (String user: users) {
            int userId = createTestUser(user, roles);
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

    private void setUserRoles(int userId, List<String> roles, int groupId) {
        try {
            for (String role : roles) {
                int roleId = UserAdminService.getRoleId(role);
                UserAdminService.addUserRole(userId, roleId, groupId);
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

    private int createTestInstance(String instanceName) {
        if (ddpInstanceId != -1) {
            throw new DsmInternalError("Test instance already created");
        }
        ddpInstanceId = createInstance(instanceName);
        SimpleResult res = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_DDP_INSTANCE_GROUP, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1, ddpInstanceId);
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
        return ddpInstanceId;
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
