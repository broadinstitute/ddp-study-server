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
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.auth0.client.mgmt.ManagementAPI;
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

    private static final Logger LOG = LoggerFactory.getLogger(Auth0ManagementClient.class);
    private static final String AUDIENCE_SUFFIX = "api/v2/";
    private static final int DEFAULT_TIMEOUT_SECS = 10;
    private static final int TOKEN_LEEWAY_SECONDS = 30;
    private static final Map<String, DecodedJWT> TOKEN_CACHE = new HashMap<>();
    private static final Gson gson = new Gson();

    private final URI baseUrl;
    private final String clientId;
    private final String clientSecret;
    // A unique key for the client we're working with for looking up the cached token.
    private final String tokenLookupKey;
    private final ManagementAPI mgmtApi;
    private final HttpClient httpClient;

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
        try {
            mgmtApi.setApiToken(getToken());
            ConnectionsPage page = mgmtApi.connections().listAll(null).execute();
            return ApiResult.ok(200, page.getItems());
        } catch (APIException e) {
            return ApiResult.err(e.getStatusCode(), e);
        } catch (Exception e) {
            return ApiResult.thrown(e);
        }
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
     * Create a new auth0 user account. Note: need database connection for creating email/password account.
     *
     * @param connection the database connection name
     * @param email      the user's email
     * @param password   the user's password
     * @return result with created user, or error response
     */
    public ApiResult<User, APIException> createAuth0User(String connection, String email, String password) {
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
    }

    /**
     * Initiate password reset flow and get the ticket URL.
     *
     * @param auth0UserId the user to do password reset for
     * @param redirectUrl the URL to go to after user has finished password reset
     * @return result with ticket URL, or error response
     */
    public ApiResult<String, APIException> createPasswordResetTicket(String auth0UserId, String redirectUrl) {
        try {
            mgmtApi.setApiToken(getToken());
            var ticket = new PasswordChangeTicket(auth0UserId);
            ticket.setResultUrl(redirectUrl);
            PasswordChangeTicket createdTicket = mgmtApi.tickets().requestPasswordChange(ticket).execute();
            return ApiResult.ok(200, createdTicket.getTicket());
        } catch (APIException e) {
            return ApiResult.err(e.getStatusCode(), e);
        } catch (Exception e) {
            return ApiResult.thrown(e);
        }
    }

    /**
     * Fetch the auth0 user. Will retry a few times if hit rate limit.
     *
     * @param auth0UserId the auth0 user id
     * @return result with the user, or error response
     */
    public ApiResult<User, APIException> getAuth0User(String auth0UserId) {
        int numTries = 3;
        ApiResult<User, APIException> res = null;
        while (numTries > 0) {
            try {
                mgmtApi.setApiToken(getToken());
                var user = mgmtApi.users().get(auth0UserId, null).execute();
                res = ApiResult.ok(200, user);
            } catch (APIException e) {
                res = ApiResult.err(e.getStatusCode(), e);
            } catch (Exception e) {
                res = ApiResult.thrown(e);
            }
            if (res.getStatusCode() == 429) {
                LOG.error("Hit rate limit while fetching auth0 user {}, retrying", auth0UserId, res.getError());
                try {
                    TimeUnit.MILLISECONDS.sleep(500L);
                } catch (InterruptedException e) {
                    LOG.warn("Interrupted while waiting after rate limit", e);
                }
            } else {
                break;
            }
            numTries--;
        }
        return res;
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
