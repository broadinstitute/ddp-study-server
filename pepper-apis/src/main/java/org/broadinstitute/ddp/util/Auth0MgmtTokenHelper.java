package org.broadinstitute.ddp.util;

import java.io.IOException;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;
import org.broadinstitute.ddp.exception.DDPException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class that caches a management API token
 * and manages getting a new one when needed.
 */
public class Auth0MgmtTokenHelper {

    private static final Logger LOG = LoggerFactory.getLogger(Auth0MgmtTokenHelper.class);
    private static final int MGMT_API_SECONDS_LEEWAY = 30;
    private final String mgmtApiClientId;
    private final String mgmtApiClientSecret;
    private final String auth0Domain;
    private DecodedJWT managementToken;

    /**
     * Instantiates Auth0MgmtTokenHelper object.
     */
    public Auth0MgmtTokenHelper(String mgmtApiClientId, String mgmtApiClientSecret, String auth0BaseUrl) {
        this.mgmtApiClientId = mgmtApiClientId;
        this.mgmtApiClientSecret = mgmtApiClientSecret;
        this.auth0Domain = auth0BaseUrl;
    }

    /**
     * Returns true if the cached token is about to expire or if it hasn't been initialized.
     */
    private boolean needNewToken() {
        return (managementToken == null) || (managementToken.getExpiresAt().getTime() < System.currentTimeMillis()
                + (MGMT_API_SECONDS_LEEWAY * 1000));
    }

    public String getManagementApiToken() {
        return getValidManagementToken();
    }

    /**
     * Generates a new auth0 management token or uses the
     * cached token if it hasn't expired.
     */
    private synchronized String getValidManagementToken() {
        if (needNewToken()) {
            try {
                managementToken = generateNewManagementToken();
            } catch (IOException e) {
                throw new RuntimeException("Error generating management API token", e);
            }
        }
        return managementToken.getToken();
    }

    /**
     * Requests a new management API token directly from auth0 APIs.
     */
    private DecodedJWT generateNewManagementToken() throws IOException {
        LOG.info("Getting new auth0 mgmt token");
        ManagementAPIPayload payload = new ManagementAPIPayload(mgmtApiClientId, mgmtApiClientSecret, auth0Domain);
        // todo arz config-ify
        Request request = Request.Post(auth0Domain + Auth0Util.REFRESH_ENDPOINT)
                .bodyString(new Gson().toJson(payload), ContentType.APPLICATION_JSON);
        ManagementAPIResponse mgmtApiResponse = request.execute().handleResponse(httpResponse -> {
            String responseString = EntityUtils.toString(httpResponse.getEntity());
            int status = httpResponse.getStatusLine().getStatusCode();
            if (status == 200) {
                return new Gson().fromJson(responseString, ManagementAPIResponse.class);
            } else {
                throw new DDPException("Attempt to get management token failed with status: " + status + ":" + responseString);
            }
        });

        LOG.info("Got new auth0 mgmt token");
        return JWT.decode(mgmtApiResponse.getAccessToken());
    }

    public String getDomain() {
        return auth0Domain;
    }

    public static class ManagementAPIResponse {

        @SerializedName("access_token")
        private String accessToken;

        public String getAccessToken() {
            return accessToken;
        }
    }

    private static class ManagementAPIPayload {

        private static final transient String API_V2 = "api/v2/";

        @SerializedName("audience")
        private String audience;

        @SerializedName("grant_type")
        private String grantType = "client_credentials";

        @SerializedName("client_id")
        private String clientId;

        @SerializedName("client_secret")
        private String clientSecret;

        public ManagementAPIPayload(String mgmtApiClientId, String mgmtApiClientSecret, String auth0BaseUrl) {
            clientId = mgmtApiClientId;
            clientSecret = mgmtApiClientSecret;
            audience = auth0BaseUrl + API_V2;
        }
    }
}
