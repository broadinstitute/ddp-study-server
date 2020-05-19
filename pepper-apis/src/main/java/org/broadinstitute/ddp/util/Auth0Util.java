package org.broadinstitute.ddp.util;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.auth0.client.mgmt.ManagementAPI;
import com.auth0.client.mgmt.filter.ConnectionFilter;
import com.auth0.client.mgmt.filter.UserFilter;
import com.auth0.exception.APIException;
import com.auth0.exception.Auth0Exception;
import com.auth0.json.auth.TokenHolder;
import com.auth0.json.mgmt.ConnectionsPage;
import com.auth0.json.mgmt.tickets.PasswordChangeTicket;
import com.auth0.json.mgmt.users.User;
import com.auth0.json.mgmt.users.UsersPage;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.typesafe.config.Config;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;
import org.broadinstitute.ddp.client.Auth0ManagementClient;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.dao.JdbiAuth0Tenant;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dto.Auth0TenantDto;
import org.broadinstitute.ddp.db.dto.EnrollmentStatusDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.json.auth0.Auth0CallResponse;
import org.broadinstitute.ddp.security.JWTConverter;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Auth0Util {
    private static final Logger LOG = LoggerFactory.getLogger(Auth0Util.class);

    public static final String USERNAME_PASSWORD_AUTH0_CONN_NAME = "Username-Password-Authentication";
    private static final String HTTPS_PREFIX = "https://";
    public static final String PEPPER_USER_GUIDS_USER_APP_METADATA_KEY = "pepper_user_guids";
    public static final String REFRESH_ENDPOINT = "oauth/token";
    private final String baseUrl;
    // map of cached jwk providers so we don't hammer auth0
    private static final Map<String, JwkProvider> jwkProviderMap = new HashMap<>();

    public Auth0Util(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * Parses out leading https and trailing / if
     * present in the domain name, as auth0 needs the
     * bare domain as the audience.
     */
    private static String parseBareDomain(String domain) {
        String bareDomain = domain;
        if (bareDomain.startsWith(HTTPS_PREFIX)) {
            bareDomain = bareDomain.replace(HTTPS_PREFIX, "");
            if (bareDomain.endsWith("/")) {
                bareDomain = bareDomain.substring(0, bareDomain.length() - 1);
            }
        }
        return bareDomain;
    }

    /**
     * Adds the ddp user.guid to the given auth0 user's app_metadata.
     * The app_metadata contains a mapping between each client and
     * the user.guid so that different dev clients can operate with the
     * same user without trampling on the user.guid.
     */
    public void setDDPUserGuidForAuth0User(String ddpUserGuid, String auth0UserId, String auth0ClientId, String mgmtApiToken) {
        String bareDomain = parseBareDomain(baseUrl);
        ManagementAPI auth0Mgmt = new ManagementAPI(bareDomain, mgmtApiToken);
        LOG.info("About to update auth0 user {} with ddp guid {} for client {}.",
                auth0UserId, ddpUserGuid, auth0ClientId);
        try {
            User auth0User = auth0Mgmt.users().get(auth0UserId, null).execute();
            Map<String, Object> appMetadata = auth0User.getAppMetadata();
            if (appMetadata == null) {
                appMetadata = new HashMap<>();
            }
            if (!appMetadata.containsKey(PEPPER_USER_GUIDS_USER_APP_METADATA_KEY)) {
                appMetadata.put(PEPPER_USER_GUIDS_USER_APP_METADATA_KEY, new HashMap<>());
            }
            Map<String, String> guidByClientId =
                    (Map<String, String>) appMetadata.get(PEPPER_USER_GUIDS_USER_APP_METADATA_KEY);
            guidByClientId.put(auth0ClientId, ddpUserGuid);
            User updatedUser = new User();
            updatedUser.setAppMetadata(appMetadata);
            auth0Mgmt.users().update(auth0UserId, updatedUser).execute();
        } catch (Auth0Exception e) {
            throw new RuntimeException("Failed to get auth0 user " + auth0UserId, e);
        }
        LOG.info("Updated auth0 user {} with ddp guid {} for client {}.", auth0UserId, ddpUserGuid, auth0ClientId);
    }

    /**
     * Verifies the JWT and decodes it.  Safe to use everywhere.
     */
    public static String getVerifiedAuth0UserId(String idToken, String auth0Domain) {
        JwkProvider jwkProvider = null;
        synchronized (jwkProviderMap) {
            jwkProvider = jwkProviderMap.get(auth0Domain);
            if (jwkProvider == null) {
                jwkProvider = new JwkProviderBuilder(auth0Domain).cached(100, 3L, TimeUnit.HOURS).build();
                jwkProviderMap.put(auth0Domain, jwkProvider);
                LOG.info("Created new jwk provider for {} ", auth0Domain);
            }
        }
        DecodedJWT verifiedJWT = JWTConverter.verifyDDPToken(idToken, jwkProvider);
        return verifiedJWT.getSubject();
    }


    /**
     * Queries the study for its management api creds
     * and returns a new management api client.
     */
    public static Auth0ManagementClient getManagementClientForStudy(Handle handle, String studyGuid) {
        Auth0TenantDto auth0TenantDto = handle.attach(JdbiAuth0Tenant.class).findByStudyGuid(studyGuid);
        return new Auth0ManagementClient(
                auth0TenantDto.getDomain(),
                auth0TenantDto.getManagementClientId(),
                auth0TenantDto.getManagementClientSecret());
    }

    /**
     * Get management api client using credentials for the given domain.
     */
    public static Auth0ManagementClient getManagementClientForDomain(Handle handle, String auth0Domain) {
        Auth0TenantDto auth0TenantDto = handle.attach(JdbiAuth0Tenant.class).findByDomain(auth0Domain);
        return new Auth0ManagementClient(
                auth0TenantDto.getDomain(),
                auth0TenantDto.getManagementClientId(),
                auth0TenantDto.getManagementClientSecret());
    }

    public static boolean isUserCredentialsValid(
            String userGuid, String userName, String password, String auth0ClientId, Handle handle) {

        Auth0TenantDto auth0TenantDto = handle.attach(JdbiAuth0Tenant.class).findByUserGuid(userGuid);
        String studyGuid;
        List<EnrollmentStatusDto> enrollmentList = handle.attach(JdbiUserStudyEnrollment.class).findByUserGuid(userGuid);
        if (CollectionUtils.isNotEmpty(enrollmentList)) {
            studyGuid = enrollmentList.get(0).getStudyGuid();
            //todo need to revisit to handle users registered in multiple studies
        } else {
            LOG.warn("User : {} not enrolled in any study ", userGuid);
            return false;
        }

        boolean isValid = false;
        try {
            isValid = verifyAuth0UserCredentials(auth0ClientId, auth0TenantDto.getDomain(),
                    userName, password, studyGuid);
        } catch (IOException e) {
            LOG.warn("Attempt to verify auth0 user: {} by user credentials failed with error: {}", userGuid, e);
        }
        return isValid;
    }

    private static boolean verifyAuth0UserCredentials(String auth0ClientId, String auth0Domain,
                                                      String userName, String password, String studyGuid) throws IOException {

        //try to get new token with passed username and pwd. If call is SUCCESS, its valid credentials
        UserLoginPayload payload =
                new UserLoginPayload(auth0ClientId, userName, password, studyGuid);
        Request request = Request.Post(auth0Domain + Auth0Util.REFRESH_ENDPOINT)
                .bodyString(new Gson().toJson(payload), ContentType.APPLICATION_JSON);
        boolean isValid = request.execute().handleResponse(httpResponse -> {
            String responseString = EntityUtils.toString(httpResponse.getEntity());
            int status = httpResponse.getStatusLine().getStatusCode();
            if (status == 200) {
                return true;
            } else {
                LOG.warn("Attempt to verify user: {} by user credentials failed with status: {} : Error: {}",
                        userName, status, responseString);
                return false;
            }
        });
        return isValid;
    }

    public static Auth0ManagementClient getManagementClientForUser(Handle handle, String userGuid) {
        Auth0TenantDto auth0TenantDto = handle.attach(JdbiAuth0Tenant.class).findByUserGuid(userGuid);
        return new Auth0ManagementClient(
                auth0TenantDto.getDomain(),
                auth0TenantDto.getManagementClientId(),
                auth0TenantDto.getManagementClientSecret());
    }

    /**
     * Updates the user's data in Auth0
     *
     * @param mgmtAPI     Management API instance used to call Auth0
     * @param userDto     Holds the user GUID and Auth0 ID besides other data
     * @param newUserData A new user data to be updated in Auth0
     * @return The result of the Auth0 call
     */
    private static Auth0CallResponse updateUserData(ManagementAPI mgmtAPI, UserDto userDto, User newUserData) {
        // Calling Auth0
        String userGuid = userDto.getUserGuid();
        LOG.info("Trying to update the data for the user {}. Auth0 user id = {}", userGuid, userDto.getAuth0UserId());
        User response = null;
        try {
            response = mgmtAPI.users().update(userDto.getAuth0UserId(), newUserData).execute();
        } catch (APIException e) {
            // A specific Auth0 API issue occurred. Relay the status code
            String errMsg = "Auth0 API call failed with the code " + e.getStatusCode() + ". Reason: " + e.getMessage()
                    + ". Description: " + e.getDescription();
            LOG.error(errMsg + ". User GUID: {}, Auth0 user id {}", userGuid, userDto.getAuth0UserId(), e);
            return new Auth0CallResponse(e.getStatusCode(), errMsg);
        } catch (Auth0Exception e) {
            // An unspecified Auth0 API issue occurred. Return HTTP 500
            String errMsg = "Auth0 API call failed. Reason: " + e.getMessage();
            LOG.error(errMsg + ". User GUID: {}, Auth0 user id {}", userGuid, userDto.getAuth0UserId(), e);
            return new Auth0CallResponse(500, errMsg);
        }
        // All fine
        LOG.info("Data for the user {} (Auth0 user id {}) was updated successfully", userGuid, userDto.getAuth0UserId());
        return new Auth0CallResponse(200, null);
    }

    /**
     * Updates the user's email in Auth0
     *
     * @param mgmtAPI  Auth0 Management API instance
     * @param userDto  user for which you want to update the email
     * @param newEmail A new email of this user
     * @return The result of the Auth0 call
     */
    public static Auth0CallResponse updateUserEmail(
            ManagementAPI mgmtAPI,
            UserDto userDto,
            String newEmail
    ) {
        User payload = new User();
        payload.setEmail(newEmail);
        LOG.info("Trying to set the email to {} for the user {}", newEmail, userDto.getUserGuid());
        return updateUserData(mgmtAPI, userDto, payload);
    }

    /**
     * Updates the user's password in Auth0
     *
     * @param mgmtAPI     Auth0 Management API instance
     * @param userDto     user for which you want to update the password
     * @param newPassword A new password of this user
     * @return The result of the Auth0 call
     */
    public static Auth0CallResponse updateUserPassword(
            ManagementAPI mgmtAPI,
            UserDto userDto,
            String newPassword
    ) {
        User payload = new User();
        payload.setPassword(newPassword);
        LOG.info("Trying to set the password for the user {}", userDto.getUserGuid());
        return updateUserData(mgmtAPI, userDto, payload);
    }


    /**
     * Exchanges the auth0 one-time code for a refresh token, id token, and access token.
     *
     * @param authCode    the code value returned from auth0 during login
     * @param redirectUri the url of the site the user logged in from
     */
    public RefreshTokenResponse getRefreshTokenFromCode(String authCode, String auth0ClientId,
                                                        String auth0ClientSecret, String redirectUri) {
        RequestRefreshTokenPayload payload = new RequestRefreshTokenPayload(auth0ClientId,
                auth0ClientSecret, authCode, redirectUri);
        Request request = Request.Post(baseUrl + REFRESH_ENDPOINT)
                .bodyString(new Gson().toJson(payload), ContentType.APPLICATION_JSON);

        Response response = null;
        String responseBody = null;
        try {
            response = request.execute();
            AtomicBoolean responseOk = new AtomicBoolean(false);
            responseBody = response.handleResponse(httpResponse -> {
                responseOk.set(200 == httpResponse.getStatusLine().getStatusCode());
                return EntityUtils.toString(httpResponse.getEntity());
            });
            if (!responseOk.get()) {
                throw new RuntimeException("Could not get refresh token.  Auth0 responded with " + responseBody);
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not get a refresh token", e);
        }
        return new Gson().fromJson(responseBody, RefreshTokenResponse.class);
    }

    /**
     * Uses the refreshToken to re-issue an access and id token.
     *
     * @param refreshToken the refresh token returned from
     *                     {@link #getRefreshTokenFromCode(String, String, String, String)}
     */
    public RefreshTokenResponse refreshToken(String auth0ClientId, String auth0ClientSecret, String refreshToken) {
        RefreshTokenPayload payload = new RefreshTokenPayload(auth0ClientId, auth0ClientSecret, refreshToken);
        Request request = Request.Post(baseUrl + REFRESH_ENDPOINT)
                .bodyString(new Gson().toJson(payload), ContentType.APPLICATION_JSON);

        try {
            return request.execute().handleResponse(new ResponseHandler<RefreshTokenResponse>() {
                @Override
                public RefreshTokenResponse handleResponse(HttpResponse httpResponse) throws IOException {
                    int status = httpResponse.getStatusLine().getStatusCode();
                    if (status == 200) {
                        return new Gson().fromJson(EntityUtils.toString(httpResponse.getEntity()),
                                RefreshTokenResponse.class);
                    } else {
                        throw new RuntimeException("Attempt to refresh token returned " + status + ":"
                                + EntityUtils.toString(httpResponse.getEntity()));
                    }
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Could not refresh token", e);
        }
    }

    public User getAuth0User(String auth0UserId, String mgmtApiToken) throws Auth0Exception {
        ManagementAPI auth0Mgmt = new ManagementAPI(baseUrl, mgmtApiToken);
        return auth0Mgmt.users().get(auth0UserId, null).execute();
    }

    /**
     * Creates a ManagementAPI instance eligible to manipulate the user in Auth0
     *
     * @param userGuid GUID of the user to manage
     * @return A ManagementAPI instance able to change the user's data in Auth0
     */
    public static ManagementAPI getManagementApiInstanceForUser(String userGuid, Handle handle) {
        var mgmtClient = Auth0Util.getManagementClientForUser(handle, userGuid);
        String mgmtToken = mgmtClient.getToken();
        String auth0Domain = mgmtClient.getDomain();
        return new ManagementAPI(auth0Domain, mgmtToken);
    }

    /**
     * Called by tests to delete Auth0 Users. Do not use in production
     *
     * @param auth0UserId  userId to be deleted
     * @param mgmtApiToken management token for the domain in question.
     * @throws Auth0Exception if Auth0 fails
     */
    public void deleteAuth0User(String auth0UserId, String mgmtApiToken) throws Auth0Exception {
        ManagementAPI auth0Mgmt = new ManagementAPI(baseUrl, mgmtApiToken);
        auth0Mgmt.users().delete(auth0UserId).execute();
    }

    /**
     * Returns all users that have the given email address
     */
    public List<User> getAuth0UsersByEmail(String emailAddress, String mgmtApiToken) throws Auth0Exception {
        ManagementAPI auth0Mgmt = new ManagementAPI(baseUrl, mgmtApiToken);
        return auth0Mgmt.users().listByEmail(emailAddress, null).execute();
    }

    /**
     * Returns all users that have the given email address and associated with passed connection
     */
    public List<User> getAuth0UsersByEmail(String emailAddress, String mgmtApiToken, String connection) throws Auth0Exception {
        ManagementAPI auth0Mgmt = new ManagementAPI(baseUrl, mgmtApiToken);
        String query = "email:" + emailAddress + " AND identities.connection:" + connection;
        UserFilter userFilter = new UserFilter().withQuery(query);
        return auth0Mgmt.users().list(userFilter).execute().getItems();
    }

    public Map<String, String> getAuth0UsersByEmails(Set<String> emailIds, String mgmtApiToken) {
        if (emailIds == null || emailIds.isEmpty()) {
            return new HashMap<>();
        }

        int maxPerPage = 100;
        Map<String, String> results = new HashMap<>(); //<email, userId>
        List<String> ids = new ArrayList<>(emailIds);
        ManagementAPI auth0Mgmt = new ManagementAPI(baseUrl, mgmtApiToken);

        for (int i = 0; i < ids.size(); i += maxPerPage) {
            int end = Math.min(i + maxPerPage, ids.size());
            List<String> subset = ids.subList(i, end);

            String query = String.format("identities.connection:\"%s\" AND email:(%s)",
                    USERNAME_PASSWORD_AUTH0_CONN_NAME, String.join(" ", subset));

            UserFilter filter = new UserFilter()
                    .withFields("user_id,email", true)
                    .withPage(0, 100)
                    .withQuery(query)
                    .withSearchEngine("v3");

            try {
                UsersPage page = auth0Mgmt.users().list(filter).execute();
                for (User user : page.getItems()) {
                    results.put(user.getEmail(), user.getId());
                }
            } catch (Auth0Exception e) {
                LOG.error("Error while retrieving auth0 user ids via email lookup, continuing pagination", e);
            }
        }

        return results;
    }

    /**
     * Finds email for each auth0UserId. Since Auth0 API is paginated, this will attempt to work through all given auth0UserIds but any
     * API errors during the process will be consumed. This means results might not contain all requested user emails and caller should
     * handle such scenario.
     *
     * <p>Note: this is restricted to querying emails from the Username-Password-Authentication Connection.
     *
     * @param auth0UserIds the auth0UserIds to query emails for
     * @param mgmtApiToken management token to access API
     * @return mapping of auth0UserId to email
     */
    public Map<String, String> getUserPassConnEmailsByAuth0UserIds(Set<String> auth0UserIds, String mgmtApiToken) {
        if (auth0UserIds == null || auth0UserIds.isEmpty()) {
            return new HashMap<>();
        }

        int maxPerPage = 100;
        Map<String, String> results = new HashMap<>();
        List<String> ids = new ArrayList<>(auth0UserIds);
        ManagementAPI auth0Mgmt = new ManagementAPI(baseUrl, mgmtApiToken);

        // IMPORTANT: in order to satisfy certain limits and restrictions, especially URL length limits, we "pagination"
        // through the authUserIds. 100 auth0UserIds in the Lucene query seems like a safe limit. And it works out that
        // the max items per page from the API is 100.
        for (int i = 0; i < ids.size(); i += maxPerPage) {
            int end = Math.min(i + maxPerPage, ids.size());
            List<String> subset = ids.subList(i, end);

            // NOTE: Lucene syntax likes OR operator, but using a space also works. We do the latter to save space on URL limit.
            String query = String.format("identities.connection:\"%s\" AND user_id:(%s)",
                    USERNAME_PASSWORD_AUTH0_CONN_NAME, String.join(" ", subset));

            UserFilter filter = new UserFilter()
                    .withFields("user_id,email", true)
                    .withPage(0, 100)
                    .withQuery(query)
                    .withSearchEngine("v3");

            try {
                UsersPage page = auth0Mgmt.users().list(filter).execute();
                for (User user : page.getItems()) {
                    results.put(user.getId(), user.getEmail());
                }
            } catch (Auth0Exception e) {
                LOG.error("Error while retrieving auth0 user emails, continuing pagination", e);
            }
        }

        return results;
    }

    /**
     * Creates a new, unregistered user
     */
    public TestingUser createTestingUser(String mgmtApiToken) throws Auth0Exception {
        ManagementAPI auth0Mgmt = new ManagementAPI(baseUrl, mgmtApiToken);
        User testUser = new User();
        testUser.setEmail("test+" + System.currentTimeMillis() + "@datadonationplatform.org");
        String password = Double.toString(Math.random()) + "Aa1";
        testUser.setPassword(password);
        testUser.setConnection(USERNAME_PASSWORD_AUTH0_CONN_NAME);
        User createdUser = auth0Mgmt.users().create(testUser).execute();
        return new TestingUser(createdUser.getId(), createdUser.getEmail(), password);
    }

    /**
     * Creates a new user (unregistered migration user)
     */
    public User createAuth0User(String emailId, String pwd, String mgmtApiToken) throws Auth0Exception {
        ManagementAPI auth0Mgmt = new ManagementAPI(baseUrl, mgmtApiToken);
        User user = new User();
        user.setEmail(emailId);
        user.setPassword(pwd);
        user.setConnection(USERNAME_PASSWORD_AUTH0_CONN_NAME);
        User createdUser = auth0Mgmt.users().create(user).execute();
        return createdUser;
    }

    /**
     * Will generate a URL that will direct to Auth0 and allow them to change password
     *
     * @param userEmail    the email for the user
     * @param connectionId the Auth0 connection id {@link #generatePasswordResetLink}
     * @param mgmtApiToken Auth0 token with Management API grant
     * @param redirectUrl  Auth0 will redirect to this URL after user has reset the password
     * @return a String containing the Auth0 reset password URL
     * @throws Auth0Exception if there is a problem
     */
    public String generatePasswordResetLink(String userEmail, String connectionId, String mgmtApiToken, String redirectUrl)
            throws Auth0Exception {
        ManagementAPI mgmt = new ManagementAPI(baseUrl, mgmtApiToken);
        PasswordChangeTicket ticket = new PasswordChangeTicket(userEmail, connectionId);
        ticket.setResultUrl(redirectUrl);
        com.auth0.net.Request<PasswordChangeTicket> request = mgmt.tickets().requestPasswordChange(ticket);
        LOG.info("Creating password reset for {}", userEmail);
        return request.execute().getTicket();
    }

    /**
     * Get the username-password connection id. Needed to execute other Auth0 API calls.
     *
     * @param mgmtApiToken the Auth0 management account token with grant to read connection
     * @return A String with the connection id
     * @throws Auth0Exception exception returned by Auth0 API
     */
    public String getAuth0UserNamePasswordConnectionId(String mgmtApiToken) throws Auth0Exception {
        ManagementAPI mgmt = new ManagementAPI(baseUrl, mgmtApiToken);
        com.auth0.net.Request<ConnectionsPage> connectionsRequest = mgmt.connections().listAll(
                new ConnectionFilter().withName(USERNAME_PASSWORD_AUTH0_CONN_NAME));
        return connectionsRequest.execute().getItems().stream()
                .findFirst()
                .map(connection -> connection.getId())
                .orElseThrow(() -> new RuntimeException("Did not find Auth0 connection with name: " + USERNAME_PASSWORD_AUTH0_CONN_NAME));
    }

    /**
     * Auth0 account deletion .
     * You probably should not ever call this unless no other option.
     */
    public static void deleteUserFromAuth0(Handle handle, Config cfg, String email) throws Auth0Exception {
        Config auth0Config = cfg.getConfig(ConfigFile.AUTH0);
        String auth0Domain = auth0Config.getString(ConfigFile.DOMAIN);
        LOG.info("Domain : {} ", auth0Domain);

        Auth0TenantDto auth0TenantDto = handle.attach(JdbiAuth0Tenant.class).findByDomain(auth0Domain);
        var mgmtClient = new Auth0ManagementClient(
                auth0Domain,
                auth0TenantDto.getManagementClientId(),
                auth0TenantDto.getManagementClientSecret());

        String mgmtToken = mgmtClient.getToken();
        Auth0Util auth0Util = new Auth0Util(auth0Domain);
        String connectionId = auth0Util.getAuth0UserNamePasswordConnectionId(mgmtToken);
        ManagementAPI mgmtAPI = new ManagementAPI(auth0Domain, mgmtToken);
        mgmtAPI.connections().deleteUser(connectionId, email).execute();
    }

    /**
     * Generate a JWT token with a short expiration time. Useful for creating auth tokens required by DSM.
     *
     * @param algorithm the algorithm to sign the token with
     * @param issuer    the token issuer
     * @return token string
     * @throws com.auth0.jwt.exceptions.JWTCreationException if failed to create token
     */
    public static String generateShortLivedJwtToken(Algorithm algorithm, String issuer) {
        long expiresAt = Instant.now().plus(1, ChronoUnit.MINUTES).toEpochMilli();
        JWTCreator.Builder builder = JWT.create();
        builder.withIssuer(issuer);
        builder.withExpiresAt(new Date(expiresAt));
        return builder.sign(algorithm);
    }

    /**
     * Wrapper class around {@link User} and {@link TokenHolder} that
     * keeps track of password, user guid, and post-logged-in token.
     */
    public static class TestingUser {

        private Long userId;
        private String userGuid;
        private String userHruid;

        private String auth0Id;
        private String email;
        private String password;
        private String token;

        public TestingUser(String auth0Id, String email, String password) {
            this.auth0Id = auth0Id;
            this.email = email;
            this.password = password;
        }

        public TestingUser(Long userId, String userGuid, String userHruid, String auth0Id, String email, String password, String token) {
            this.userId = userId;
            this.userGuid = userGuid;
            this.userHruid = userHruid;
            this.auth0Id = auth0Id;
            this.email = email;
            this.password = password;
            this.token = token;
        }

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public String getUserGuid() {
            return userGuid;
        }

        public void setUserGuid(String userGuid) {
            this.userGuid = userGuid;
        }

        public String getUserHruid() {
            return userHruid;
        }

        public void setUserHruid(String userHruid) {
            this.userHruid = userHruid;
        }

        public String getAuth0Id() {
            return auth0Id;
        }

        public void setAuth0Id(String auth0Id) {
            this.auth0Id = auth0Id;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }
    }

    private static class RefreshTokenPayload {

        @SerializedName("grant_type")
        private String grantType = "refresh_token";

        @SerializedName("client_id")
        private String clientId;

        @SerializedName("client_secret")
        private String clientSecret;

        @SerializedName("refresh_token")
        private String refreshToken;

        @SerializedName("scope")
        private String scope = "openid profile";

        public RefreshTokenPayload(String clientId, String clientSecret, String refreshToken) {
            this.clientId = clientId;
            this.clientSecret = clientSecret;
            this.refreshToken = refreshToken;
        }
    }

    public static class RefreshTokenResponse {

        @SerializedName("id_token")
        private String idToken;

        @SerializedName("access_token")
        private String accessToken;

        @SerializedName("expires_in")
        private int expiresIn;

        /**
         * only set on initial exchange from {@link #getRefreshTokenFromCode(String, String, String, String)}.
         **/
        @SerializedName("refresh_token")
        private String refreshToken;

        /**
         * Instantiates RefreshTokenResponse object.
         */
        public RefreshTokenResponse(String idToken, String accessToken, int expiresIn) {
            this.idToken = idToken;
            this.accessToken = accessToken;
            this.expiresIn = expiresIn;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public String getIdToken() {
            return idToken;
        }

        public int getExpiresIn() {
            return expiresIn;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

    }

    public static class RequestRefreshTokenPayload {

        @SerializedName("grant_type")
        private String grantType = "authorization_code";

        @SerializedName("client_id")
        private String auth0ClientId;

        @SerializedName("client_secret")
        private String auth0Secret;

        @SerializedName("code")
        private String auth0Code;

        @SerializedName("redirect_uri")
        private String redirectUri;

        /**
         * Instantiates RequestRefreshTokenPayload object.
         */
        public RequestRefreshTokenPayload(String auth0ClientId,
                                          String auth0Secret,
                                          String auth0Code,
                                          String redirectUri) {
            this.auth0ClientId = auth0ClientId;
            this.auth0Secret = auth0Secret;
            this.auth0Code = auth0Code;
            this.redirectUri = redirectUri;
        }

    }

    private static class UserLoginPayload {

        @SerializedName("username")
        private String userName;

        @SerializedName("password")
        private String password;

        @SerializedName("grant_type")
        private String grantType = "password";

        @SerializedName("client_id")
        private String clientId;

        @SerializedName("study_guid")
        private String studyGuid;

        public UserLoginPayload(String auth0ClientId, String userName, String password, String studyGuid) {
            clientId = auth0ClientId;
            this.userName = userName;
            this.password = password;
            this.studyGuid = studyGuid;
        }
    }

}
