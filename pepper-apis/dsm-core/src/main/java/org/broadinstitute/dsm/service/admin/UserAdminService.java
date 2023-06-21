package org.broadinstitute.dsm.service.admin;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.lddp.db.SimpleResult;
import spark.utils.StringUtils;

@Slf4j
public class UserAdminService {

    private final String operatorId;

    private static final String STUDY_ADMIN_ROLE = "study_admin";

    private static final String SQL_SELECT_ASSIGNEE =
            "SELECT user.user_id, user.name, user.email FROM access_user_role_group roleGroup, access_user user, access_role role, "
                    + "ddp_group, ddp_instance_group realmGroup, ddp_instance realm WHERE roleGroup.user_id = user.user_id AND roleGroup"
                    + ".role_id = role.role_id AND realm.ddp_instance_id = realmGroup.ddp_instance_id"
                    + " AND realmGroup.ddp_group_id = ddp_group.group_id AND ddp_group.group_id = roleGroup.group_id "
                    + "AND role.name = \"mr_request\" AND realm.instance_name = ?";

    private static final String SQL_SELECT_ROLES_FOR_GROUP_AND_USER_ID =
            "SELECT ar.role_id, ar.name, dg.group_id FROM access_role ar "
                    + "JOIN access_user_role_group aurg on aurg.role_id = ar.role_id "
                    + "JOIN ddp_group dg on dg.group_id = aurg.group_id "
                    + "JOIN access_user au on au.user_id = aurg.user_id "
                    + "WHERE au.user_id = ? AND dg.name = ?";

    private static final String SQL_SELECT_USER_ROLE =
            "SELECT aurg.user_role_group_id FROM access_user_role_group aurg "
                    + "JOIN access_role ar on ar.role_id = aurg.role_id "
                    + "JOIN ddp_group dg on dg.group_id = aurg.group_id "
                    + "JOIN access_user au on au.user_id = aurg.user_id "
                    + "WHERE au.user_id = ? AND dg.name = ? AND ar.name = ?";

    private static final String SQL_SELECT_ROLE =
            "SELECT ar.role_id FROM access_role ar WHERE ar.name = ?";

    private static final String SQL_SELECT_GROUP =
            "SELECT dg.group_id FROM ddp_group dg WHERE dg.name = ?";

    private static final String SQL_SELECT_USER_BY_EMAIL_AND_GROUP_ID =
            "select distinct(au.user_id), au.name FROM access_user au "
                    + "JOIN access_user_role_group aurg on aurg.user_id = au.user_id "
                    + "JOIN ddp_group dg on aurg.group_id = ? "
                    + "WHERE au.email = ?";

    private static final String SQL_INSERT_USER_ROLE =
            "INSERT INTO access_user_role_group SET user_id = ?, role_id = ?, group_id = ?";

    private static final String SQL_DELETE_USER_ROLE =
            "DELETE FROM access_user_role_group WHERE user_id = ? AND role_id = ?";

    private static final String SQL_INSERT_ROLE =
            "INSERT INTO access_role SET name = ?";

    private static final String SQL_INSERT_GROUP =
            "INSERT INTO ddp_group SET name = ?";

    private static final String SQL_DELETE_GROUP =
            "DELETE FROM ddp_group WHERE group_id = ?";

    public UserAdminService(String operatorId) {
        this.operatorId = operatorId;
    }

    public void addUserToRole(AddUserRoleRequest req) {

        // TODO: determine if operator can admin this study group user

        String group = req.getStudyGroup();
        if (StringUtils.isBlank(group)) {
            throw new DSMBadRequestException("Invalid study group: blank");
        }
        String email = req.getEmail();
        if (StringUtils.isBlank(email)) {
            throw new DSMBadRequestException("Invalid user email: blank");
        }
        String role = req.getRole();
        if (StringUtils.isBlank(role)) {
            throw new DSMBadRequestException("Invalid role: blank");
        }

        int groupId = verifyOperatorForGroup(operatorId, group);

        int roleId = verifyRole(role, groupId);

        int userId = getUserByEmailAndGroup(email, groupId);

        try {
            addUserRole(userId, roleId, groupId);
        } catch (Exception e) {
            String msg = String.format("Error adding user %s to role %s", email, role);
            throw new DsmInternalError(msg, e);
        }
    }

    public void addStudyRole(AddStudyRoleRequest req) {
        String group = req.getStudyGroup();
        if (StringUtils.isBlank(group)) {
            throw new DSMBadRequestException("Invalid study group: blank");
        }
        String role = req.getRole();
        if (StringUtils.isBlank(role)) {
            throw new DSMBadRequestException("Invalid role: blank");
        }

        int groupId = verifyOperatorForGroup(operatorId, group);

        try {
            addRole(role, groupId);
        } catch (Exception e) {
            String msg = String.format("Error adding role %s to study group %s", role, group);
            throw new DsmInternalError(msg, e);
        }
    }

    protected static int verifyOperatorForGroup(String operatorId, String studyGroup) {

        List<RoleAndGroup> roles = getRolesForUser(operatorId, studyGroup);
        if (roles.isEmpty()) {
            throw new DSMBadRequestException("No roles found for operator: " + operatorId);
        }
        List<String> roleNames = roles.stream().map(r -> r.roleName).collect(Collectors.toList());
        if (!roleNames.contains(STUDY_ADMIN_ROLE)) {
            throw new DSMBadRequestException("Operator does not have administrator privileges: " + operatorId);
        }
        return roles.get(0).groupId;
    }

    protected static List<RoleAndGroup> getRolesForUser(String userId, String studyGroup) {
        SimpleResult res = inTransaction(conn -> {
            List<RoleAndGroup> roles = new ArrayList<>();
            SimpleResult dbVals = new SimpleResult(roles);
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_ROLES_FOR_GROUP_AND_USER_ID)) {
                stmt.setString(1, userId);
                stmt.setString(2, studyGroup);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        roles.add(new RoleAndGroup(rs.getInt(1), rs.getString(2), rs.getInt(3)));
                    }
                }
            } catch (SQLException e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });

        if (res.resultException != null) {
            throw new DsmInternalError("Error getting study groups for user", res.resultException);
        }
        return (List<RoleAndGroup>) res.resultValue;
    }

    protected static int verifyRole(String role, int groupId) {
        // TODO: Currently we do not track the valid roles for a group, but verify with
        // groupId once we do -DC
        SimpleResult res = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_ROLE)) {
                stmt.setString(1, role);
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
            String msg = String.format("Error getting role %s for study group, ID: %d", role, groupId);
            throw new DsmInternalError(msg, res.resultException);
        }

        if (res.resultValue == null) {
            throw new DSMBadRequestException("Invalid role for study group: " + role);
        }
        return (int) res.resultValue;
    }

    protected static int verifyStudyGroup(String studyGroup) {
        SimpleResult res = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_GROUP)) {
                stmt.setString(1, studyGroup);
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
            String msg = String.format("Error getting study group %s", studyGroup);
            throw new DsmInternalError(msg, res.resultException);
        }

        if (res.resultValue == null) {
            throw new DSMBadRequestException("Invalid study group: " + studyGroup);
        }
        return (int) res.resultValue;
    }

    protected static int getUserByEmailAndGroup(String email, int groupId) {
        SimpleResult res = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_USER_BY_EMAIL_AND_GROUP_ID)) {
                stmt.setInt(1, groupId);
                stmt.setString(2, email);
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
            String msg = String.format("Error getting user %s for study group, ID: %d", email, groupId);
            throw new DsmInternalError(msg, res.resultException);
        }

        if (res.resultValue == null) {
            throw new DSMBadRequestException("Invalid user for study group: " + email);
        }
        return (int) res.resultValue;
        // TODO: see if access_user.is_active is used -DC
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

    protected static boolean addUserRole(int userId, int roleId, int groupId) throws Exception {
        // since we do not have a key constraint for duplicate entries we need to check first
        int userRoleId = getUserRole(userId, roleId, groupId);
        if (userRoleId != -1) {
            return false;
        }
        SimpleResult res = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_USER_ROLE)) {
                stmt.setInt(1, userId);
                stmt.setInt(2, roleId);
                stmt.setInt(3, groupId);
                int result = stmt.executeUpdate();
                if (result != 1) {
                    dbVals.resultException = new DsmInternalError("Error adding user to role. Result count was " + result);
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (res.resultException != null) {
            throw res.resultException;
        }
        return true;
    }

    protected static int addRole(String role, int groupId) throws Exception {
        // TODO: Currently we do not track the valid roles for a group, but enroll with
        // groupId once we do -DC
        SimpleResult res = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_ROLE, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, role);
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
            throw new DsmInternalError("Error adding role " + role, res.resultException);
        }
        return (int) res.resultValue;
    }

    protected static int addStudyGroup(String groupName) {
        SimpleResult res = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_GROUP, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, groupName);
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
            throw new DsmInternalError("Error adding study group " + groupName, res.resultException);
        }
        return (int) res.resultValue;
    }

    protected static int deleteUserRole(int userId, int roleId) {
        SimpleResult results = inTransaction(conn -> {
            SimpleResult res = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_DELETE_USER_ROLE)) {
                stmt.setInt(1, userId);
                stmt.setInt(2, roleId);
                res.resultValue = stmt.executeUpdate();
            } catch (SQLException ex) {
                res.resultException = ex;
            }
            return res;
        });
        if (results.resultException != null) {
            String msg = String.format("Error deleting user role: userId=%d, roleId=%d", userId, roleId);
            throw new DsmInternalError(msg, results.resultException);
        }
        return (int) results.resultValue;
    }

    protected static int deleteStudyGroup(int groupId) {
        SimpleResult results = inTransaction(conn -> {
            SimpleResult res = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_DELETE_GROUP)) {
                stmt.setInt(1, groupId);
                res.resultValue = stmt.executeUpdate();
            } catch (SQLException ex) {
                res.resultException = ex;
            }
            return res;
        });
        if (results.resultException != null) {
            String msg = String.format("Error deleting study group: groupId=%d", groupId);
            throw new DsmInternalError(msg, results.resultException);
        }
        return (int) results.resultValue;
    }

    private static class RoleAndGroup {
        public final int roleId;
        public final String roleName;
        public final int groupId;

        public RoleAndGroup(int roleId, String roleName, int groupId) {
            this.roleId = roleId;
            this.roleName = roleName;
            this.groupId = groupId;
        }
    }
}
