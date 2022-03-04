package org.broadinstitute.lddp.security;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.auth0.client.auth.AuthAPI;
import com.auth0.client.mgmt.ManagementAPI;
import com.auth0.json.auth.TokenHolder;
import com.auth0.json.mgmt.users.Identity;
import com.auth0.json.mgmt.users.User;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.net.AuthRequest;
import com.auth0.net.Request;
import lombok.NonNull;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.exception.AuthenticationException;
import org.broadinstitute.dsm.security.JWTConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Auth0Util {

    private static final Logger logger = LoggerFactory.getLogger(Auth0Util.class);
    private final String account;
    private final String ddpKey;
    private final String ddpSecret;
    private final String mgtApiUrl;
    private final List<String> connections;
    private byte[] decodedSecret;
    private AuthAPI ddpAuthApi = null;
    private AuthAPI mgtAuthApi = null;
    private boolean emailVerificationRequired;
    private String audience;
    private String token;
    private Long expiresAt;

    public Auth0Util(@NonNull String account, @NonNull List<String> connections, boolean secretEncoded,
                     @NonNull String ddpKey, @NonNull String ddpSecret,
                     @NonNull String mgtKey, @NonNull String mgtSecret, @NonNull String mgtApiUrl, boolean emailVerificationRequired,
                     String audience) {
        this.ddpSecret = ddpSecret;

        byte[] tempSecret = ddpSecret.getBytes();
        if (secretEncoded) {
            tempSecret = Base64.decodeBase64(ddpSecret);
        }
        this.decodedSecret = tempSecret;

        this.account = account;
        this.ddpKey = ddpKey;
        this.connections = connections;
        this.ddpAuthApi = new AuthAPI(account, ddpKey, ddpSecret);
        this.mgtAuthApi = new AuthAPI(account, mgtKey, mgtSecret);
        this.mgtApiUrl = mgtApiUrl;
        this.emailVerificationRequired = emailVerificationRequired;
        this.audience = audience;
    }

    public Auth0UserInfo getAuth0UserInfo(@NonNull String idToken, String auth0Domain) throws AuthenticationException {
        try {
            Map<String, Claim> auth0Claims = verifyAndParseAuth0TokenClaims(idToken, auth0Domain);
            Auth0UserInfo userInfo = new Auth0UserInfo(auth0Claims.get("email").asString(), auth0Claims.get("exp").asInt());
            verifyUserConnection(auth0Claims.get("sub").asString(), userInfo.getEmail());

            return userInfo;
        } catch (AuthenticationException e) {
            throw new AuthenticationException("couldn't get Auth0 user info", e);
        }
    }

    private ManagementAPI configManagementApi() {
        TokenHolder tokenHolder = null;
        try {
            AuthRequest requestToken = mgtAuthApi.requestToken(mgtApiUrl);
            tokenHolder = requestToken.execute();

            if (tokenHolder.getAccessToken() == null) {
                throw new RuntimeException("Unable to retrieve access token.");
            }
        } catch (Exception ex) {
            throw new RuntimeException("Unable to generate token using management client.", ex);
        }

        String mgmtToken = tokenHolder.getAccessToken(); //these tokens only last 24 hours by default!!

        return new ManagementAPI(account, mgmtToken);
    }

    private String findUserConnection(List<Identity> list) {
        String connection = null;
        for (Identity identity : list) {
            for (String auth0Connection : connections) {
                if (auth0Connection.equals(identity.getConnection())) {
                    connection = auth0Connection;
                    break;
                }
            }
        }

        if (connection == null) {
            throw new RuntimeException("User does not have an approved connection.");
        }
        return connection;
    }

    private void verifyUserConnection(@NonNull String userId, @NonNull String email) {//todo pegah check
        try {
            ManagementAPI mgmtApi = configManagementApi();
            Request<User> userRequest = mgmtApi.users().get(userId, null);
            User user = userRequest.execute();
            findUserConnection(user.getIdentities());
        } catch (Exception ex) {
            throw new RuntimeException("User connection verification failed for user " + email, ex);
        }
    }

    public static Map<String, Claim> verifyAndParseAuth0TokenClaims(String auth0Token, String auth0Domain) throws AuthenticationException {
        Map<String, Claim> auth0Claims = new HashMap<>();
        try {
            Optional<DecodedJWT> maybeToken = JWTConverter.verifyDDPToken(auth0Token, auth0Domain);
            maybeToken.orElseThrow();
            auth0Claims = maybeToken.get().getClaims();
        } catch (Exception e) {
            throw new AuthenticationException("Could not verify auth0 token.", e);
        }
        return auth0Claims;
    }

    /**
     * Auth0 token for pepper communication
     *
     * @return
     */
    public String getAccessToken() {
        if (token != null && StringUtils.isNotBlank(token) && expiresAt != null) {
            long minFromNow = System.currentTimeMillis() + (60 * 5);
            if (expiresAt < minFromNow) {
                logger.info("Token will expire in less than 5 min.");
                token = null;
            }
        }
        if (token == null) {
            if (StringUtils.isNotBlank(audience)) {
                try {
                    AuthRequest request = ddpAuthApi.requestToken(audience);
                    TokenHolder tokenHolder = request.execute();
                    token = tokenHolder.getAccessToken();
                    expiresAt = System.currentTimeMillis() + (tokenHolder.getExpiresIn() * 1000);
                    logger.info("Generated new token for auth0.");
                } catch (Exception ex) {
                    throw new RuntimeException("Unable to get access token for audience " + audience, ex);
                }
            } else {
                throw new RuntimeException("Auth0 Audience is missing.");
            }
        }
        return token;
    }

    public static class Auth0UserInfo {

        private String email;
        private long expirationTime;

        public Auth0UserInfo(@NonNull Object emailObj, @NonNull Object expirationTimeObj) {
            this.email = emailObj.toString();
            this.expirationTime = Long.parseLong(expirationTimeObj.toString());
        }

        public String getEmail() {
            return email;
        }

        public long getTokenExpiration() {
            return expirationTime;
        }
    }
}
