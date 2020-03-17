package org.broadinstitute.ddp.client;

import java.util.List;
import java.util.stream.Collectors;

import com.auth0.client.mgmt.ManagementAPI;
import com.auth0.exception.APIException;
import com.auth0.json.mgmt.Connection;
import com.auth0.json.mgmt.ConnectionsPage;
import org.broadinstitute.ddp.util.Auth0MgmtTokenHelper;

/**
 * A client wrapper for Auth0's Management API.
 */
public class Auth0ManagementClient {

    public static final String DB_CONNECTION_STRATEGY = "auth0";
    public static final String DEFAULT_DB_CONN_NAME = "Username-Password-Authentication";

    public static final String KEY_PASSWORD_POLICY = "passwordPolicy";
    public static final String KEY_PASSWORD_COMPLEXITY_OPTIONS = "password_complexity_options";
    public static final String KEY_MIN_LENGTH = "min_length";

    private final ManagementAPI mgmtApi;
    private final Auth0MgmtTokenHelper tokenHelper;

    public Auth0ManagementClient(String auth0BaseUrl, String mgmtClientId, String mgmtClientSecret) {
        // Use empty token to initialize. The token should be set before every API request since they can expire.
        this(new ManagementAPI(auth0BaseUrl, ""), new Auth0MgmtTokenHelper(mgmtClientId, mgmtClientSecret, auth0BaseUrl));
    }

    public Auth0ManagementClient(ManagementAPI mgmtApi, Auth0MgmtTokenHelper tokenHelper) {
        this.mgmtApi = mgmtApi;
        this.tokenHelper = tokenHelper;
    }

    /**
     * Get list of all connections in the tenant.
     *
     * @return result with list of connections, or error response
     */
    public ApiResult<List<Connection>, APIException> listConnections() {
        try {
            mgmtApi.setApiToken(tokenHelper.getManagementApiToken());
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
}
