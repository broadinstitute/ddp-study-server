package org.broadinstitute.ddp.route;

import java.io.IOException;
import java.nio.file.Paths;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.auth0.exception.Auth0Exception;
import com.auth0.jwt.JWT;
import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.broadinstitute.ddp.constants.Auth0Constants;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.ConfigFile.Auth0Testing;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.constants.SqlConstants;
import org.broadinstitute.ddp.constants.TestConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.UserDaoFactory;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.db.dao.JdbiAuth0Tenant;
import org.broadinstitute.ddp.db.dao.JdbiProfile;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dto.ActivityValidationDto;
import org.broadinstitute.ddp.db.dto.Auth0TenantDto;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.util.ConfigManager;
import org.broadinstitute.ddp.util.RouteUtil;
import org.broadinstitute.ddp.util.TestingUserUtil;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Utilities for route integration tests.
 */
public class RouteTestUtil {

    private static final Logger LOG = LoggerFactory.getLogger(RouteTestUtil.class);
    private static final String SQL_CONF_FILE = "sql.conf";
    private static final int DB_MAX_CONN = 2;

    private static final String DELETE_USER_BY_AUTH0USERID_STMT = "delete from user where auth0_user_id = ?";
    private static final String DELETE_USER_BY_GUID_STMT = "delete from user where guid = ?";
    private static final String DELETE_GOV_OF_PART_STMT =
            "delete from user_governance where participant_user_id = (select user_id from user where guid = ?)";

    private static String cfgPath;
    private static Config cfg;
    private static Config sqlConfig;

    /**
     * Loads configuration.
     */
    public static void loadConfig() {
        try {
            cfgPath = Paths.get(System.getProperty("config.file")).toAbsolutePath().toString();
            cfg = ConfigManager.getInstance().getConfig();
        } catch (Exception e) {
            throw new RuntimeException("Cloud not load configurations", e);
        }
    }

    /**
     * Load sql configuration.
     */
    public static void loadSqlConfig() {
        try {
            sqlConfig = ConfigFactory.parseResources(SQL_CONF_FILE);
        } catch (Exception e) {
            throw new RuntimeException("Could not load '" + SQL_CONF_FILE + "' configuration.");
        }
    }

    /**
     * Set up database pool, instance of transaction wrapper.
     */
    public static void setupDatabasePool() {
        boolean doDbInitialization = false;
        try {
            TransactionWrapper.getInstance();
            LOG.info("db pool already initialized");
        } catch (IllegalStateException e) {
            doDbInitialization = true;
        }

        if (doDbInitialization) {
            String dbUrl = cfg.getString(TransactionWrapper.DB.APIS.getDbUrlConfigKey());
            LOG.info("Initializing db pool for {}", dbUrl);
            TransactionWrapper.reset();
            TransactionWrapper.init(new TransactionWrapper.DbConfiguration(TransactionWrapper.DB.APIS, DB_MAX_CONN,
                    dbUrl));
        }
    }

    public static String getAuth0TestClientId() {
        return cfg.getConfig(ConfigFile.AUTH0).getString(Auth0Testing.AUTH0_CLIENT_ID);
    }

    public static Config getConfig() {
        return cfg;
    }

    public static String getConfigFilePath() {
        return cfgPath;
    }

    public static Config getSqlConfig() {
        return sqlConfig;
    }

    public static String getTestingBaseUrl() {
        return cfg.getString(ConfigFile.TESTING_BASE_URL);
    }

    public static String loginStaticTestUserForToken() throws Auth0Exception {
        return TransactionWrapper.withTxn(handle -> TestingUserUtil
                .loginTestUser(handle, cfg.getConfig(ConfigFile.AUTH0)).getToken());
    }

    public static String loginStaticAdminUserForToken() throws Auth0Exception {
        return TransactionWrapper.withTxn(handle -> TestingUserUtil
                .loginTestAdminUser(handle, cfg.getConfig(ConfigFile.AUTH0)).getToken());
    }

    public static Header buildTestUserAuthHeader() throws Auth0Exception {
        String token = loginStaticTestUserForToken();
        return new BasicHeader(RouteConstants.AUTHORIZATION, RouteUtil.makeAuthBearerHeader(token));
    }

    public static Header buildTestAdminAuthHeader() throws Auth0Exception {
        String token = loginStaticAdminUserForToken();
        return new BasicHeader(RouteConstants.AUTHORIZATION, RouteUtil.makeAuthBearerHeader(token));
    }

    public static Request buildAuthorizedGetRequest(String token, String url) {
        return Request.Get(url).addHeader(new BasicHeader("Authorization", "Bearer " + token));
    }

    public static Request buildAuthorizedDeleteRequest(String token, String url) {
        return Request.Delete(url).addHeader(new BasicHeader("Authorization", "Bearer " + token));
    }

    public static Request buildAuthorizedPostRequest(String token, String url, String payload) {
        Request request = Request.Post(url).addHeader(new BasicHeader("Authorization", "Bearer " + token));
        if (payload != null) {
            request.bodyString(payload, ContentType.APPLICATION_JSON);
        }
        return request;
    }

    public static Request buildAuthorizedPutRequest(String token, String url, String payload) {
        Request request = Request.Put(url).addHeader(new BasicHeader("Authorization", "Bearer " + token));
        if (payload != null) {
            request.bodyString(payload, ContentType.APPLICATION_JSON);
        }
        return request;
    }

    public static Request buildAuthorizedPatchRequest(String token, String url, String payload) {
        Request request = Request.Patch(url).addHeader(new BasicHeader("Authorization", "Bearer " + token));
        if (payload != null) {
            request.bodyString(payload, ContentType.APPLICATION_JSON);
        }
        return request;
    }

    /**
     * Sends a GET/POST/PUT/PATCH request using the token, url and payload provided
     * url must be rendered before using thus a caller must also specify the replacements
     * For examples check DsmExitUserRouteTest
     */
    public static HttpResponse sendRequestAndReturnResponse(
            RequestMethod requestMethod,
            String url,
            Map<String, String> urlReplacements,
            String dsmClientAccessToken,
            String payload
    ) throws Exception {
        String[] dummy = new String[] {url};
        urlReplacements.entrySet().forEach(
                es -> {
                    dummy[0] = dummy[0].replace(es.getKey(), es.getValue());
                }
        );
        String renderedUrl = dummy[0];
        LOG.info("Sending the " + requestMethod + " request to {}", renderedUrl);
        Request request = null;
        if (requestMethod == RequestMethod.GET) {
            request = RouteTestUtil.buildAuthorizedGetRequest(
                    dsmClientAccessToken,
                    renderedUrl
            );
        } else if (requestMethod == RequestMethod.POST) {
            request = RouteTestUtil.buildAuthorizedPostRequest(
                    dsmClientAccessToken,
                    renderedUrl,
                    payload
            );
        } else if (requestMethod == RequestMethod.PUT) {
            request = RouteTestUtil.buildAuthorizedPutRequest(
                    dsmClientAccessToken,
                    renderedUrl,
                    payload
            );
        } else if (requestMethod == RequestMethod.PATCH) {
            request = RouteTestUtil.buildAuthorizedPatchRequest(
                    dsmClientAccessToken,
                    renderedUrl,
                    payload
            );
        }
        HttpResponse response = request.execute().returnResponse();
        return response;
    }

    private static void setLockFlagForTestUser(boolean isLocked, boolean isAdmin) throws SQLException {
        TransactionWrapper.withTxn(handle -> {
            PreparedStatement stmt = handle.getConnection().prepareStatement("update user set "
                    + SqlConstants.IS_USER_LOCKED
                    + "= ? where " + SqlConstants
                    .DDP_USER_GUID + " = ?");
            stmt.setBoolean(1, isLocked);
            if (isAdmin) {
                stmt.setString(2, TestConstants.TEST_ADMIN_GUID);
            } else {
                stmt.setString(2, TestConstants.TEST_USER_GUID);
            }
            int numRows = stmt.executeUpdate();
            if (numRows != 1) {
                throw new RuntimeException("Could not lock user " + TestConstants.TEST_USER_GUID
                        + " because " + numRows + " rows were updated");
            }
            return null;
        });
    }

    private static void setTestClientIsRevoked(boolean isRevoked) throws SQLException {
        String auth0ClientId = getAuth0TestClientId();
        TransactionWrapper.withTxn(handle -> {
            try (PreparedStatement stmt = handle.getConnection()
                    .prepareStatement("update client set is_revoked = ? where auth0_client_id = ?")) {
                stmt.setBoolean(1, isRevoked);
                stmt.setString(2, auth0ClientId);
                int numRowsUpdated = stmt.executeUpdate();
                LOG.info("updated {} client rows", numRowsUpdated);
            }
            return null;
        });
    }

    /**
     * Sets the is_revoked column in the client table for the test client to true.
     */
    public static void revokeTestClient() throws SQLException {
        setTestClientIsRevoked(true);
    }

    /**
     * Sets the is_revoked column in the client table for the test client to false.
     */
    public static void enableTestClient() throws SQLException {
        setTestClientIsRevoked(false);
    }

    /**
     * Sets the is_locked column in the user table for the test user to true.
     */
    public static void disableTestUserAccount(boolean isAdmin) throws SQLException {
        setLockFlagForTestUser(true, isAdmin);
    }

    /**
     * Sets the is_locked column in the user table for the test user to false.
     */
    public static void enableTestUserAccount(boolean isAdmin) throws SQLException {
        setLockFlagForTestUser(false, isAdmin);
    }

    /**
     * Deletes user from database.
     */
    public static void deleteUserByAuth0UserId(String auth0UserId, String auth0Domain) {
        TransactionWrapper.withTxn(handle -> {
            JdbiUser userDao = handle.attach(JdbiUser.class);
            Auth0TenantDto auth0TenantDto = handle.attach(JdbiAuth0Tenant.class).findByDomain(auth0Domain);
            Long userId = userDao.getUserIdByAuth0UserId(auth0UserId, auth0TenantDto.getId());
            handle.attach(DataExportDao.class).deleteDataSyncRequestsForUser(userId);
            JdbiProfile userProfileDao = handle.attach(JdbiProfile.class);
            userProfileDao.deleteByUserId(userId);
            try (PreparedStatement stmt = handle.getConnection().prepareStatement(DELETE_USER_BY_AUTH0USERID_STMT)) {
                stmt.setString(1, auth0UserId);
                int deleted = stmt.executeUpdate();
                if (deleted != 1) {
                    LOG.error("Deleted {} rows for auth0 user id {}", deleted, auth0UserId);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    /**
     * Deletes profiles for given user given guid.
     */
    public static void deleteProfilesForUserGuid(String userGuid) throws SQLException {
        TransactionWrapper.withTxn(handle -> {
            Long userId = UserDaoFactory.createFromSqlConfig(sqlConfig).getUserIdByGuid(handle, userGuid);
            if (userId != null) {
                deleteProfileForUserId(handle, userId);
            }
            return null;
        });
    }

    /**
     * Deletes profiles for given user given id.
     */
    public static void deleteProfileForUserId(Handle handle, Long userId) throws SQLException {
        LOG.info("Deleting profile for user {} ", userId);

        try (PreparedStatement stmt = handle.getConnection()
                .prepareStatement("delete from user_profile where user_id = ?")) {
            stmt.setLong(1, userId);
            int numRowsDeleted = stmt.executeUpdate();
            if (numRowsDeleted != 1) {
                LOG.info("Removed {} user_profile rows for user_id {}", numRowsDeleted, userId);
            }
        }
    }

    /**
     * Deletes governed participants by their guid.
     */
    public static void deleteGovernedParticipantsByGuid(Collection<String> guidsToDelete) throws SQLException {
        TransactionWrapper.withTxn(handle -> {
            if (!guidsToDelete.isEmpty()) {
                for (String guid : guidsToDelete) {
                    try (PreparedStatement stmt = handle.getConnection().prepareStatement(DELETE_GOV_OF_PART_STMT)) {
                        stmt.setString(1, guid);
                        int deleted = stmt.executeUpdate();
                        if (deleted != 1) {
                            LOG.error("Deleted {} rows for guid {}", deleted, guid);
                        }
                    }
                    try (PreparedStatement stmt = handle.getConnection().prepareStatement(DELETE_USER_BY_GUID_STMT)) {
                        stmt.setString(1, guid);
                        int deleted = stmt.executeUpdate();
                        if (deleted != 1) {
                            LOG.error("Deleted {} user rows for guid {}", deleted, guid);
                        }
                    }
                }
            }
            return null;
        });
    }

    public static <T> T parseJson(HttpResponse response, Class<T> klass) throws IOException {
        String json = EntityUtils.toString(response.getEntity());
        return new Gson().fromJson(json, klass);
    }

    /**
     * Parses the ddp user guid from the token but does not verify it.
     * DO NOT USE THIS OUTSIDE OF THE CONTEXT OF A TEST.
     */
    public static String getUnverifiedUserGuidFromToken(String jwtToken) {
        return JWT.decode(jwtToken).getClaim(Auth0Constants.DDP_USER_ID_CLAIM).asString();
    }

    public static ActivityValidationDto createActivityValidationDto(
            FormActivityDef activity, String precond, String expr, String errorMessage, List<String> affectedQuestionStableIds
    ) {
        Template errorMessageTemplate = Template.text(errorMessage);
        ActivityValidationDto dto = new ActivityValidationDto(
                activity.getActivityId(), null, precond, expr, errorMessageTemplate
        );
        affectedQuestionStableIds.forEach(
                field -> dto.addAffectedField(field)
        );
        return dto;
    }

    public static ActivityValidationDto createActivityValidationDto(
            FormActivityDef activity, String expr, String errorMessage, List<String> affectedQuestionStableIds
    ) {
        return createActivityValidationDto(activity, null, expr, errorMessage, affectedQuestionStableIds);
    }

    public enum RequestMethod {
        GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS, TRACE;
    }
}
