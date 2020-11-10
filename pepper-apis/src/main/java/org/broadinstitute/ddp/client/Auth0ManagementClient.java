package org.broadinstitute.ddp.client;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.auth0.client.mgmt.ManagementAPI;
import com.auth0.client.mgmt.filter.ConnectionFilter;
import com.auth0.exception.APIException;
import com.auth0.json.mgmt.Connection;
import com.auth0.json.mgmt.ConnectionsPage;
import com.auth0.json.mgmt.tickets.PasswordChangeTicket;
import com.auth0.json.mgmt.users.User;
import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import org.apache.http.HttpHeaders;
import org.apache.http.entity.ContentType;
import org.broadinstitute.ddp.db.dao.JdbiAuth0Tenant;
import org.broadinstitute.ddp.db.dto.Auth0TenantDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A client wrapper for Auth0's Management API.
 */
public class Auth0ManagementClient {

    public static final String PATH_OAUTH_TOKEN = "/oauth/token";

    public static final String DB_CONNECTION_STRATEGY = "auth0";
    public static final String DEFAULT_DB_CONN_NAME = "Username-Password-Authentication";

    public static final String KEY_PASSWORD_POLICY = "passwordPolicy";
    public static final String KEY_PASSWORD_COMPLEXITY_OPTIONS = "password_complexity_options";
    public static final String KEY_MIN_LENGTH = "min_length";

    public static final String APP_METADATA_PEPPER_USER_GUIDS = "pepper_user_guids";

    private static final Logger LOG = LoggerFactory.getLogger(Auth0ManagementClient.class);
    private static final String AUDIENCE_SUFFIX = "api/v2/";
    private static final int DEFAULT_TIMEOUT_SECS = 10;
    private static final int TOKEN_LEEWAY_SECONDS = 30;
    private static final Map<String, DecodedJWT> TOKEN_CACHE = new HashMap<>();
    private static final Gson gson = new Gson();

    // Rate limit and retries
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final long DEFAULT_BACKOFF_MILLIS = 500L;
    private static final int MAX_JITTER_MILLIS = 100;

    private final URI baseUrl;
    private final String clientId;
    private final String clientSecret;
    // A unique key for the client we're working with for looking up the cached token.
    private final String tokenLookupKey;
    private final ManagementAPI mgmtApi;
    private final HttpClient httpClient;
    private int maxRetries;
    private long backoffMillis;

    /**
     * Find the auth0 tenant for the user and create Auth0 Management client for it.
     *
     * @param handle   the database handle
     * @param userGuid the user guid
     * @return the management client
     */
    public static Auth0ManagementClient forUser(Handle handle, String userGuid) {
        Auth0TenantDto auth0TenantDto = handle.attach(JdbiAuth0Tenant.class).findByUserGuid(userGuid);
        return new Auth0ManagementClient(
                auth0TenantDto.getDomain(),
                auth0TenantDto.getManagementClientId(),
                auth0TenantDto.getManagementClientSecret());
    }

    /**
     * Find the auth0 tenant for the study and create Auth0 Management client for it.
     *
     * @param handle    the database handle
     * @param studyGuid the study guid
     * @return the management client
     */
    public static Auth0ManagementClient forStudy(Handle handle, String studyGuid) {
        Auth0TenantDto auth0TenantDto = handle.attach(JdbiAuth0Tenant.class).findByStudyGuid(studyGuid);
        return new Auth0ManagementClient(
                auth0TenantDto.getDomain(),
                auth0TenantDto.getManagementClientId(),
                auth0TenantDto.getManagementClientSecret());
    }

    public Auth0ManagementClient(String auth0Domain, String mgmtClientId, String mgmtClientSecret) {
        // Use empty token to initialize. The token should be set before every API request since they can expire.
        this(auth0Domain, mgmtClientId, mgmtClientSecret, new ManagementAPI(auth0Domain, ""));
    }

    public Auth0ManagementClient(String auth0Domain, String mgmtClientId, String mgmtClientSecret, ManagementAPI mgmtApi) {
        try {
            this.baseUrl = new URL(auth0Domain).toURI();
        } catch (MalformedURLException | URISyntaxException e) {
            throw new IllegalArgumentException("Invalid auth0 domain", e);
        }
        this.clientId = mgmtClientId;
        this.clientSecret = mgmtClientSecret;
        this.tokenLookupKey = baseUrl.toString() + mgmtClientId;
        this.mgmtApi = mgmtApi;
        this.httpClient = HttpClient.newHttpClient();
        this.maxRetries = DEFAULT_MAX_RETRIES;
        this.backoffMillis = DEFAULT_BACKOFF_MILLIS;
    }

    /**
     * Get the auth0 domain this client talks to. Auth0 domains are expected to have a trailing slash, and this
     * guarantees to return it as such.
     *
     * @return auth0 domain
     */
    public String getDomain() {
        String domain = baseUrl.toString();
        if (!domain.endsWith("/")) {
            return domain + "/";
        } else {
            return domain;
        }
    }

    /**
     * Get access token for Auth0's Management API, ensuring that it has not expired yet.
     *
     * @return token
     */
    public String getToken() {
        if (shouldRequestToken()) {
            generateNewToken();
        }
        return TOKEN_CACHE.get(tokenLookupKey).getToken();
    }

    private synchronized void generateNewToken() {
        // Upon entrance, do another check in case it has already been generated.
        if (!shouldRequestToken()) {
            return;
        }
        LOG.info("Getting new auth0 management token");
        var result = getAccessTokenByClientCreds();
        result.rethrowIfThrown(e -> new DDPException("Error generating management API token", e));
        if (result.hasError()) {
            String msg = String.format(
                    "Attempt to get management token failed with status: %d body: %s",
                    result.getStatusCode(), result.getError());
            throw new DDPException(msg);
        }
        TOKEN_CACHE.put(tokenLookupKey, result.getBody());
        LOG.info("Got and cached new auth0 management token");
    }

    private boolean shouldRequestToken() {
        DecodedJWT token = TOKEN_CACHE.get(tokenLookupKey);
        return (token == null) || (token.getExpiresAt().getTime() < System.currentTimeMillis() + (TOKEN_LEEWAY_SECONDS * 1000));
    }

    /**
     * Get access token by following the client credentials flow.
     *
     * @return result with decoded access token, or error response
     */
    private ApiResult<DecodedJWT, String> getAccessTokenByClientCreds() {
        try {
            var payload = new ClientCredsPayload(getDomain(), clientId, clientSecret);
            var request = HttpRequest.newBuilder()
                    .uri(baseUrl.resolve(PATH_OAUTH_TOKEN))
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                    .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
                    .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECS))
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            if (statusCode == 200) {
                var resp = gson.fromJson(response.body(), ClientCredsResponse.class);
                return ApiResult.ok(statusCode, JWT.decode(resp.getAccessToken()));
            } else {
                return ApiResult.err(statusCode, response.body());
            }
        } catch (IOException | InterruptedException | JsonSyntaxException | JWTDecodeException e) {
            return ApiResult.thrown(e);
        }
    }

    /**
     * Get list of all connections in the tenant.
     *
     * @return result with list of connections, or error response
     */
    public ApiResult<List<Connection>, APIException> listConnections() {
        String msg = String.format("Hit rate limit while listing connections for tenant '%s', retrying", baseUrl);
        return withRetries(msg, () -> {
            try {
                mgmtApi.setApiToken(getToken());
                ConnectionsPage page = mgmtApi.connections().listAll(null).execute();
                return ApiResult.ok(200, page.getItems());
            } catch (APIException e) {
                return ApiResult.err(e.getStatusCode(), e);
            } catch (Exception e) {
                return ApiResult.thrown(e);
            }
        });
    }

    /**
     * Get list of connections enabled for the given client.
     *
     * @param clientId the auth0 client id
     * @return result with list of connections, or error response
     */
    public ApiResult<List<Connection>, APIException> listClientConnections(String clientId) {
        ApiResult<List<Connection>, APIException> res = listConnections();
        if (res.hasBody()) {
            List<Connection> matches = res.getBody().stream()
                    .filter(conn -> conn.getEnabledClients().contains(clientId))
                    .collect(Collectors.toList());
            return new ApiResult<>(res.getStatusCode(), matches, res.getError());
        } else {
            return res;
        }
    }

    /**
     * Get connection in the tenant by name.
     *
     * @return result with the connection, or error response
     */
    public ApiResult<Connection, APIException> getConnectionByName(String name) {
        String msg = String.format("Hit rate limit while getting connection with name '%s', retrying", name);
        var filter = new ConnectionFilter().withName(name);
        return withRetries(msg, () -> {
            try {
                mgmtApi.setApiToken(getToken());
                ConnectionsPage page = mgmtApi.connections().listAll(filter).execute();
                return ApiResult.ok(200, page.getItems().get(0));
            } catch (APIException e) {
                return ApiResult.err(e.getStatusCode(), e);
            } catch (Exception e) {
                return ApiResult.thrown(e);
            }
        });
    }

    /**
     * Create a new auth0 user account. Note: need database connection for creating email/password account.
     *
     * @param connection the database connection name
     * @param email      the user's email
     * @param password   the user's password
     * @return result with created user, or error response
     */
    public ApiResult<User, APIException> createAuth0User(String connection, String email, String password) {
        String msg = String.format(
                "Hit rate limit while creating auth0 user with email %s in connection %s, retrying",
                email, connection);
        return withRetries(msg, () -> {
            try {
                mgmtApi.setApiToken(getToken());
                User user = new User();
                user.setEmail(email);
                user.setPassword(password);
                user.setConnection(connection);
                User createdUser = mgmtApi.users().create(user).execute();
                return ApiResult.ok(200, createdUser);
            } catch (APIException e) {
                return ApiResult.err(e.getStatusCode(), e);
            } catch (Exception e) {
                return ApiResult.thrown(e);
            }
        });
    }

    /**
     * Fetch the auth0 user. Will retry a few times if hit rate limit.
     *
     * @param auth0UserId the auth0 user id
     * @return result with the user, or error response
     */
    public ApiResult<User, APIException> getAuth0User(String auth0UserId) {
        String msg = "Hit rate limit while fetching auth0 user " + auth0UserId + ", retrying";
        return withRetries(msg, () -> {
            try {
                mgmtApi.setApiToken(getToken());
                var user = mgmtApi.users().get(auth0UserId, null).execute();
                return ApiResult.ok(200, user);
            } catch (APIException e) {
                return ApiResult.err(e.getStatusCode(), e);
            } catch (Exception e) {
                return ApiResult.thrown(e);
            }
        });
    }

    /**
     * Delete the auth0 user. Note: this is a dangerous operation, use with caution.
     *
     * @param auth0UserId the auth0 user id
     * @return a void result, or error response
     */
    public ApiResult<Void, APIException> deleteAuth0User(String auth0UserId) {
        String msg = "Hit rate limit while deleting auth0 user " + auth0UserId + ", retrying";
        return withRetries(msg, () -> {
            try {
                mgmtApi.setApiToken(getToken());
                mgmtApi.users().delete(auth0UserId).execute();
                return ApiResult.ok(200, null);
            } catch (APIException e) {
                return ApiResult.err(e.getStatusCode(), e);
            } catch (Exception e) {
                return ApiResult.thrown(e);
            }
        });
    }

    /**
     * Update user metadata. Note that Auth0 only allows merging top-level properties in user_metadata. Any nested
     * object properties will be replaced instead of merged.
     *
     * @param auth0UserId the auth0 user id
     * @param metadata    the metadata
     * @return result with response user, or error response
     */
    public ApiResult<User, APIException> updateUserMetadata(String auth0UserId, Map<String, Object> metadata) {
        String msg = "Hit rate limit while updating user_metadata for auth0 user " + auth0UserId + ", retrying";
        return withRetries(msg, () -> {
            try {
                mgmtApi.setApiToken(getToken());
                var payload = new User();
                payload.setUserMetadata(metadata);
                var resp = mgmtApi.users().update(auth0UserId, payload).execute();
                return ApiResult.ok(200, resp);
            } catch (APIException e) {
                return ApiResult.err(e.getStatusCode(), e);
            } catch (Exception e) {
                return ApiResult.thrown(e);
            }
        });
    }

    /**
     * Update user's app metadata. Note that Auth0 only allows merging top-level properties in app_metadata. Any nested
     * object properties will be replaced instead of merged.
     *
     * @param auth0UserId the auth0 user id
     * @param appMetadata the app metadata
     * @return result with response user, or error response
     */
    public ApiResult<User, APIException> updateUserAppMetadata(String auth0UserId, Map<String, Object> appMetadata) {
        String msg = "Hit rate limit while updating app_metadata for auth0 user " + auth0UserId + ", retrying";
        return withRetries(msg, () -> {
            try {
                mgmtApi.setApiToken(getToken());
                var payload = new User();
                payload.setAppMetadata(appMetadata);
                var resp = mgmtApi.users().update(auth0UserId, payload).execute();
                return ApiResult.ok(200, resp);
            } catch (APIException e) {
                return ApiResult.err(e.getStatusCode(), e);
            } catch (Exception e) {
                return ApiResult.thrown(e);
            }
        });
    }

    /**
     * Store user's guid for the given client in their app metadata. The app_metadata contains a mapping between each
     * client and the user guid so that different dev clients can operate with the same user without trampling on the
     * user guid.
     *
     * @param auth0UserId   the auth0 user id
     * @param auth0ClientId the auth0 client id
     * @param userGuid      the user's guid
     * @return the updated auth0 user
     */
    public User setUserGuidForAuth0User(String auth0UserId, String auth0ClientId, String userGuid) {
        LOG.info("About to update auth0 user {} with user guid {} for client {}", auth0UserId, userGuid, auth0ClientId);
        var result = getAuth0User(auth0UserId);
        if (result.hasFailure()) {
            var e = result.hasThrown() ? result.getThrown() : result.getError();
            throw new DDPException("Failed to get auth0 user " + auth0UserId, e);
        }

        User auth0User = result.getBody();
        Map<String, Object> appMetadata = auth0User.getAppMetadata();
        if (appMetadata == null) {
            appMetadata = new HashMap<>();
        }
        Map<String, String> guidByClientId = (Map<String, String>) appMetadata
                .computeIfAbsent(APP_METADATA_PEPPER_USER_GUIDS, key -> new HashMap<>());
        guidByClientId.put(auth0ClientId, userGuid);

        result = updateUserAppMetadata(auth0UserId, appMetadata);
        if (result.hasFailure()) {
            var e = result.hasThrown() ? result.getThrown() : result.getError();
            throw new DDPException("Failed to update app metadata for auth0 user " + auth0UserId, e);
        }

        LOG.info("Updated auth0 user {} with user guid {} for client {}", auth0UserId, userGuid, auth0ClientId);
        return result.getBody();
    }

    /**
     * Remove user's guid for the given client in their app metadata.
     *
     * @param auth0UserId   the auth0 user id
     * @param auth0ClientId the auth0 client id
     * @return the updated auth0 user
     */
    public User removeUserGuidForAuth0User(String auth0UserId, String auth0ClientId) {
        LOG.info("About to remove user guid for auth0 user {} and client {}", auth0UserId, auth0ClientId);
        var result = getAuth0User(auth0UserId);
        if (result.hasFailure()) {
            var e = result.hasThrown() ? result.getThrown() : result.getError();
            throw new DDPException("Failed to get auth0 user " + auth0UserId, e);
        }

        User auth0User = result.getBody();
        Map<String, Object> appMetadata = auth0User.getAppMetadata();
        if (appMetadata == null) {
            appMetadata = new HashMap<>();
        }
        Map<String, String> guidByClientId = (Map<String, String>) appMetadata
                .computeIfAbsent(APP_METADATA_PEPPER_USER_GUIDS, key -> new HashMap<>());
        String guid = guidByClientId.remove(auth0ClientId);

        result = updateUserAppMetadata(auth0UserId, appMetadata);
        if (result.hasFailure()) {
            var e = result.hasThrown() ? result.getThrown() : result.getError();
            throw new DDPException("Failed to update app metadata for auth0 user " + auth0UserId, e);
        }

        LOG.info("Removed user guid {} from auth0 user {} for client {}", guid, auth0UserId, auth0ClientId);
        return result.getBody();
    }

    /**
     * Initiate password reset flow and get the ticket URL by auth0 user id.
     *
     * @param auth0UserId the user to do password reset for
     * @param redirectUrl the URL to go to after user has finished password reset
     * @return result with ticket URL, or error response
     */
    public ApiResult<String, APIException> createPasswordResetTicket(String auth0UserId, String redirectUrl) {
        String msg = "Hit rate limit while creating password reset ticket for auth0 user " + auth0UserId + ", retrying";
        var ticket = new PasswordChangeTicket(auth0UserId);
        ticket.setResultUrl(redirectUrl);
        return tryPasswordChangeRequest(ticket, msg);
    }

    /**
     * Initiate password reset flow and get the ticket URL by email and connection.
     *
     * @param email        the user's email
     * @param connectionId the auth0 connection id
     * @param redirectUrl  the URL to go to after user has finished password reset
     * @return result with ticket URL, or error response
     */
    public ApiResult<String, APIException> createPasswordResetTicket(String email, String connectionId, String redirectUrl) {
        String msg = String.format(
                "Hit rate limit while creating password reset ticket for email %s and connection %s, retrying",
                email, connectionId);
        var ticket = new PasswordChangeTicket(email, connectionId);
        ticket.setResultUrl(redirectUrl);
        return tryPasswordChangeRequest(ticket, msg);
    }

    private ApiResult<String, APIException> tryPasswordChangeRequest(PasswordChangeTicket ticket, String retryMessage) {
        return withRetries(retryMessage, () -> {
            try {
                mgmtApi.setApiToken(getToken());
                PasswordChangeTicket createdTicket = mgmtApi.tickets().requestPasswordChange(ticket).execute();
                return ApiResult.ok(200, createdTicket.getTicket());
            } catch (APIException e) {
                return ApiResult.err(e.getStatusCode(), e);
            } catch (Exception e) {
                return ApiResult.thrown(e);
            }
        });
    }

    private <B, E> ApiResult<B, E> withRetries(String retryMessage, Supplier<ApiResult<B, E>> callback) {
        ApiResult<B, E> res = null;
        int numTries = 0;
        int maxTries = maxRetries + 1;
        while (numTries < maxTries) {
            res = callback.get();
            numTries++;
            if (numTries >= maxTries) {
                break;
            }
            if (res.getStatusCode() == 429) {
                LOG.error(retryMessage, res.getError());
                long wait = backoffMillis * numTries + new Random().nextInt(MAX_JITTER_MILLIS);
                try {
                    TimeUnit.MILLISECONDS.sleep(wait);
                } catch (InterruptedException e) {
                    LOG.warn("Interrupted while waiting after rate limit", e);
                }
            } else {
                break;
            }
        }
        return res;
    }

    private static class ClientCredsPayload {
        private String audience;
        @SerializedName("grant_type")
        private String grantType = "client_credentials";
        @SerializedName("client_id")
        private String clientId;
        @SerializedName("client_secret")
        private String clientSecret;

        public ClientCredsPayload(String domain, String clientId, String clientSecret) {
            this.clientId = clientId;
            this.clientSecret = clientSecret;
            this.audience = domain + AUDIENCE_SUFFIX;
        }
    }

    private static class ClientCredsResponse {
        @SerializedName("access_token")
        private String accessToken;

        public String getAccessToken() {
            return accessToken;
        }
    }
}
