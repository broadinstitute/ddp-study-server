package org.broadinstitute.dsm.util;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.db.UserSettings;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.QueryExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Request;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

public class UserUtil {

    private static final Logger logger = LoggerFactory.getLogger(UserUtil.class);

    private static final String SQL_SELECT_USER = "SELECT user_id, name FROM access_user";
    private static final String SQL_INSERT_USER = "INSERT INTO access_user (name, email) VALUES (?,?)";
    private static final String SQL_SELECT_USER_ACCESS_ROLE = "SELECT role.name FROM access_user_role_group roleGroup, access_user user, access_role role " +
            "WHERE roleGroup.user_id = user.user_id AND roleGroup.role_id = role.role_id AND user.is_active = 1";
    public static final String SQL_USER_ROLES_PER_REALM = "SELECT role.name FROM  access_user_role_group roleGroup " +
            "LEFT JOIN ddp_instance_group gr on (gr.ddp_group_id = roleGroup.group_id) " +
            "LEFT JOIN access_user user on (roleGroup.user_id = user.user_id) " +
            "LEFT JOIN ddp_instance realm on (realm.ddp_instance_id = gr.ddp_instance_id) " +
            "LEFT JOIN access_role role on (role.role_id = roleGroup.role_id) " +
            "WHERE roleGroup.user_id = ? and instance_name = ?";
    private static final String SQL_USER_ROLES = "SELECT DISTINCT role.name FROM  access_user_role_group roleGroup " +
            "LEFT JOIN ddp_instance_group gr on (gr.ddp_group_id = roleGroup.group_id) " +
            "LEFT JOIN access_user user on (roleGroup.user_id = user.user_id) " +
            "LEFT JOIN access_role role on (role.role_id = roleGroup.role_id) " +
            "WHERE roleGroup.user_id = ? ";
    private static final String SQL_SELECT_USER_REALMS = "SELECT DISTINCT realm.instance_name, realm.display_name, (SELECT count(role.name) " +
            "FROM ddp_instance realm2, ddp_instance_role inRol, instance_role role " +
            "WHERE realm2.ddp_instance_id = inRol.ddp_instance_id AND inRol.instance_role_id = role.instance_role_id AND role.name = ? " +
            "AND realm2.ddp_instance_id = realm.ddp_instance_id) AS 'has_role' FROM access_user_role_group roleGroup, " +
            "access_user user, ddp_group, ddp_instance_group realmGroup, ddp_instance realm, access_role role " +
            "WHERE realm.ddp_instance_id = realmGroup.ddp_instance_id AND realmGroup.ddp_group_id = ddp_group.group_id AND ddp_group.group_id = roleGroup.group_id " +
            "AND roleGroup.user_id = user.user_id AND role.role_id = roleGroup.role_id AND realm.is_active = 1 AND user.is_active = 1 AND user.user_id = ? ";

    public static final String USER_ID = "userId";

    private static final String NO_USER_ROLE = "NO_USER_ROLE";

    private static final String MAILINGLIST_MENU = "mailingList";
    private static final String MEDICALRECORD_MENU = "medicalRecord";
    public static final String SHIPPING_MENU = "shipping";
    private static final String PARTICIPANT_EXIT_MENU = "participantExit";
    private static final String EMAIL_EVENT_MENU = "emailEvent";
    private static final String SURVEY_CREATION_MENU = "surveyCreation";
    private static final String PARTICIPANT_EVENT_MENU = "participantEvent";
    private static final String DISCARD_SAMPLE_MENU = "discardSamples";
    private static final String PDF_DOWNLOAD_MENU = "pdfDownload";

    public ArrayList<String> getUserAccessRoles(@NonNull String email) {
        ArrayList<String> roles = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_USER_ACCESS_ROLE + QueryExtension.BY_USER_EMAIL)) {
                stmt.setString(1, email);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        roles.add(rs.getString(DBConstants.NAME));
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting list of roles ", results.resultException);
        }
        return roles;
    }

    public static Map<Integer, String> getUserMap() {
        Map<Integer, String> users = new HashMap<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_USER)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        users.put(rs.getInt(DBConstants.USER_ID), rs.getString(DBConstants.NAME));
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting map of users ", results.resultException);
        }
        return users;
    }

    public void insertUser(@NonNull String name, @NonNull String email) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement insertStmt = conn.prepareStatement(SQL_INSERT_USER, Statement.RETURN_GENERATED_KEYS)) {
                insertStmt.setString(1, name);
                insertStmt.setString(2, email);
                insertStmt.executeUpdate();
                try (ResultSet rs = insertStmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        UserSettings.insertUserSetting(conn, rs.getInt(1));
                    }
                }
                catch (Exception e) {
                    throw new RuntimeException("Error getting id of new kit request ", e);
                }
            }
            catch (SQLException ex) {
                logger.error("User " + name + ", " + email + " already exists but doesn't have any access roles or is set to is_active=0...");
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting list of realms ", results.resultException);
        }
    }

    public static String getUserId(Request request) {
        QueryParamsMap queryParams = request.queryMap();
        String userId = "";
        if (queryParams.value(USER_ID) != null) {
            userId = queryParams.get(USER_ID).value();
        }

        if (StringUtils.isBlank(userId)) {
            throw new RuntimeException("No userId query param was sent");
        }
        return userId;
    }

    public static Collection<String> getListOfAllowedRealms(@NonNull String userId) {
        List<String> listOfRealms = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try {
                getList(conn, SQL_SELECT_USER_REALMS, NO_USER_ROLE, userId, listOfRealms);
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Couldn't get lists of allowed realms ", results.resultException);
        }
        logger.info("Found " + listOfRealms.size() + " realm for user w/ id " + userId);
        return listOfRealms;
    }

    public static List<NameValue> getAllowedStudies(@NonNull String userId) {
        List<NameValue> studies = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_USER_REALMS)) {
                stmt.setString(1, NO_USER_ROLE);
                stmt.setString(2, userId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String instanceName = rs.getString(DBConstants.INSTANCE_NAME);
                        String displayName = rs.getString(DBConstants.DISPLAY_NAME);
                        if (StringUtils.isNotBlank(displayName)) {
                            studies.add(new NameValue(instanceName, displayName));
                        }
                        else {
                            studies.add(new NameValue(instanceName, instanceName));
                        }
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting list of studies ", results.resultException);
        }
        return studies;

    }

    public static Collection<String> getListOfAllowedRealms(@NonNull String userId, @NonNull String menu) {
        List<String> listOfRealms = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try {
                String query = SQL_SELECT_USER_REALMS;
                String instanceRole = NO_USER_ROLE;
                if (MAILINGLIST_MENU.equals(menu)) {
                    query = query + QueryExtension.BY_ROLE_NAME;
                    query = query.replace("%1", DBConstants.MAILINGLIST_VIEW);
                    instanceRole = DBConstants.HAS_MAILING_LIST_ENDPOINT;
                    getList(conn, query, instanceRole, userId, listOfRealms);
                }
                else if (MEDICALRECORD_MENU.equals(menu)) {
//                    query = query + QueryExtension.BY_ROLE_NAME;
//                    query = query.replace("%1", DBConstants.MR_VIEW);
//                    getList(conn, query, instanceRole, userId, listOfRealms);
                    getList(conn, query, instanceRole, userId, listOfRealms);
                }
                else if (SHIPPING_MENU.equals(menu)) {
                    query = query + QueryExtension.BY_ROLE_NAME_START_WITH;
                    query = query.replace("%1", DBConstants.KIT_SHIPPING);
                    instanceRole = DBConstants.KIT_REQUEST_ACTIVATED;
                    getList(conn, query, instanceRole, userId, listOfRealms);
                }
                else if (PARTICIPANT_EXIT_MENU.equals(menu)) {
                    query = query + QueryExtension.BY_ROLE_NAME;
                    query = query.replace("%1", DBConstants.PARTICIPANT_EXIT);
                    getList(conn, query, instanceRole, userId, listOfRealms);
                }
                else if (EMAIL_EVENT_MENU.equals(menu)) {
                    query = query + QueryExtension.BY_ROLE_NAME;
                    query = query.replace("%1", DBConstants.EMAIL_EVENT);
                    getList(conn, query, instanceRole, userId, listOfRealms);
                }
                else if (SURVEY_CREATION_MENU.equals(menu)) {
                    query = query + QueryExtension.BY_ROLE_NAME;
                    query = query.replace("%1", DBConstants.SURVEY_CREATION);
                    instanceRole = DBConstants.SURVEY_CREATION_ENDPOINTS;
                    getList(conn, query, instanceRole, userId, listOfRealms);
                }
                else if (PARTICIPANT_EVENT_MENU.equals(menu)) {
                    query = query + QueryExtension.BY_ROLE_NAME;
                    query = query.replace("%1", DBConstants.PARTICIPANT_EVENT);
                    instanceRole = DBConstants.KIT_PARTICIPANT_NOTIFICATIONS_ACTIVATED;
                    getList(conn, query, instanceRole, userId, listOfRealms);
                }
                else if (DISCARD_SAMPLE_MENU.equals(menu)) {
                    query = query + QueryExtension.BY_ROLE_NAMES;
                    query = query.replace("%1", DBConstants.DISCARD_SAMPLE);
                    query = query.replace("%2", DBConstants.PARTICIPANT_EXIT);
                    instanceRole = DBConstants.KIT_REQUEST_ACTIVATED;
                    getList(conn, query, instanceRole, userId, listOfRealms);
                }
                else if (PDF_DOWNLOAD_MENU.equals(menu)) {
                    query = TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.GET_ALLOWED_REALMS_FOR_USER_ROLE_STARTS_LIKE);
                    query = query.replace("%1", DBConstants.PDF_DOWNLOAD);
                    query = query + QueryExtension.BY_ROLE_NAME;
                    query = query.replace("%1", DBConstants.PDF_DOWNLOAD);
                    getList(conn, query, userId, listOfRealms);
                }
                else {
                    throw new RuntimeException("Menu (" + menu + ") not found");
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Couldn't get allowed realms ", results.resultException);
        }
        logger.info("Found " + listOfRealms.size() + " realm for " + menu);
        return listOfRealms;
    }

    private static void getList(Connection conn, String query, String userId, List<String> listOfRealms) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    if (rs.getBoolean(DBConstants.HAS_ROLE)) {
                        listOfRealms.add(rs.getString(DBConstants.INSTANCE_NAME));
                    }
                }
            }
        }
    }

    private static void getList(Connection conn, String query, String role, String userId, List<String> listOfRealms) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, role);
            stmt.setString(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    if (NO_USER_ROLE.equals(role)) {
                        listOfRealms.add(rs.getString(DBConstants.INSTANCE_NAME));
                    }
                    else {
                        if (rs.getBoolean(DBConstants.HAS_ROLE)) {
                            listOfRealms.add(rs.getString(DBConstants.INSTANCE_NAME));
                        }
                    }
                }
            }
        }
    }

    public static boolean checkUserAccess(String realm, String userId, String role) {
        List<String> roles;
        if (StringUtils.isBlank(realm)) {
            roles = getUserRolesPerRealm(SQL_USER_ROLES, userId, null);
        }
        else {
            roles = getUserRolesPerRealm(SQL_USER_ROLES_PER_REALM, userId, realm);
        }
        if (roles != null && !roles.isEmpty()) {
            return roles.contains(role);
        }
        return false;
    }

    public static List<String> getUserRolesPerRealm(@NonNull String query, @NonNull String userId, String realm) {
        List<String> roles = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, userId);
                if (StringUtils.isNotBlank(realm)) {
                    stmt.setString(2, realm);
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        roles.add(rs.getString(DBConstants.NAME));
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting list of user roles ", results.resultException);
        }
        return roles;
    }
}
