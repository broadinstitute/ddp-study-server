package org.broadinstitute.lddp.security;

import com.auth0.client.auth.AuthAPI;
import com.auth0.client.mgmt.ManagementAPI;
import com.auth0.json.auth.TokenHolder;
import com.auth0.json.mgmt.users.Identity;
import com.auth0.json.mgmt.users.User;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.net.AuthRequest;
import com.auth0.net.Request;
import com.auth0.net.SignUpRequest;
import com.typesafe.config.Config;
import lombok.NonNull;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.lddp.util.Utility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Auth0Util {

    private static final Logger logger = LoggerFactory.getLogger(Auth0Util.class);

    private byte[] decodedSecret;
    private final String account, ddpKey, ddpSecret, mgtApiUrl;
    private AuthAPI ddpAuthApi = null;
    private AuthAPI mgtAuthApi = null;
    private final List<String> connections;
    private boolean emailVerificationRequired;
    private String audience;
    private String token;
    private Long expiresAt;

    public Auth0Util(@NonNull String account, @NonNull List<String> connections, boolean secretEncoded,
                     @NonNull String ddpKey, @NonNull String ddpSecret,
                     @NonNull String mgtKey, @NonNull String mgtSecret, @NonNull String mgtApiUrl, boolean emailVerificationRequired) {
        this(account, connections, secretEncoded, ddpKey, ddpSecret, mgtKey, mgtSecret, mgtApiUrl, emailVerificationRequired, null);
    }

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

    public boolean isEmailVerificationRequired() {
        return emailVerificationRequired;
    }

    public Auth0UserInfo getAuth0UserInfo(@NonNull String idToken) {
        Map<String, Claim> auth0Claims = verifyAndParseAuth0TokenClaims(idToken);
        boolean isEmailVerified = false;
        Claim emailVerifiedClaim = auth0Claims.getOrDefault("email_verified", null);
        if (emailVerifiedClaim != null && !emailVerifiedClaim.isNull()) {
            Boolean value = emailVerifiedClaim.asBoolean();
            isEmailVerified = value == null ? false : value;
        }
        Auth0UserInfo userInfo = new Auth0UserInfo(auth0Claims.get("email").asString(), auth0Claims.get("exp").asInt(),
                isEmailVerified);
        verifyUserConnection(auth0Claims.get("sub").asString(), userInfo.getEmail());

        return userInfo;
    }

    public void signUp(@NonNull String email, @NonNull String password) {
        try {
            SignUpRequest request = ddpAuthApi.signUp(email, password, connections.get(0));
            request.execute();
        }
        catch (Exception ex) {
            throw new RuntimeException("Unable to signup user with email " + email, ex);
        }
    }

    public String signUpAndGetIdToken(@NonNull String email, @NonNull String password) {
        try {
            SignUpRequest request = ddpAuthApi.signUp(email, password, connections.get(0));
            request.execute();
        }
        catch (Exception ex) {
            throw new RuntimeException("Unable to signup user with email " + email, ex);
        }

        return login(email, password);
    }

    public String login(@NonNull String email, @NonNull String password) {
        TokenHolder tokenHolder = null;
        try {
            AuthRequest request = ddpAuthApi.login(email, password, connections.get(0)).setScope("openid email");
            tokenHolder = request.execute();
        }
        catch (Exception ex) {
            throw new RuntimeException("Unable to login user with email " + email, ex);
        }

        if ((tokenHolder == null) || ((tokenHolder.getIdToken() == null))) {
            throw new RuntimeException("Could not obtain auth0 token for " + email + ".");
        }

        return tokenHolder.getIdToken();
    }

    //Only used for testing right now.
    public void verifyEmail(@NonNull Config config, @NonNull String idToken) {
        if (config.getString("portal.environment").equals(Utility.Deployment.UNIT_TEST.toString())) {
            try {
                Map<String, Claim> auth0Claims = verifyAndParseAuth0TokenClaims(idToken);
                String userId = auth0Claims.get("sub").asString();

                ManagementAPI mgmtApi = configManagementApi();
                Request<User> userRequest = mgmtApi.users().get(userId, null);
                User user = userRequest.execute();
                String connection = findUserConnection(user.getIdentities());

                if (!user.isEmailVerified()) {

                    User updateUser = new User();
                    updateUser.setEmailVerified(true);
                    updateUser.setConnection(connection);
                    userRequest = mgmtApi.users().update(userId, updateUser);
                    userRequest.execute();
                }
            }
            catch (Exception ex) {
                throw new RuntimeException("Unable to perform email verification.", ex);
            }
        }
        else {
            throw new RuntimeException("This method is for testing purposes only! ENV=" + config.getString("portal.environment"));
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
        }
        catch (Exception ex) {
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

    private void verifyUserConnection(@NonNull String userId, @NonNull String email) {
        try {
            ManagementAPI mgmtApi = configManagementApi();
            Request<User> userRequest = mgmtApi.users().get(userId, null);
            User user = userRequest.execute();
            findUserConnection(user.getIdentities());
        }
        catch (Exception ex) {
            throw new RuntimeException("User connection verification failed for user " + email, ex);
        }
    }

    public Map<String, Claim> verifyAndParseAuth0TokenClaims(String auth0Token) {
        Map<String, Claim> auth0Claims = new HashMap<>();
        try {
            Algorithm algorithm = Algorithm.HMAC256(decodedSecret);
            JWTVerifier verifier = JWT.require(algorithm).withIssuer(account).build(); //Reusable verifier instance
            DecodedJWT jwt = verifier.verify(auth0Token);
            auth0Claims = jwt.getClaims();
        }
        catch (Exception e) {
            throw new RuntimeException("Could not verify auth0 token.", e);
        }
        return auth0Claims;
    }

    public static Auth0Util configureAuth0Util(@NonNull Config config) {
        Auth0Util auth0Util = null;
        if (useAuth0(config)) {
            auth0Util = new Auth0Util(config.getString("auth0.account"), config.getStringList("auth0.connections"), config.getBoolean("auth0.isSecretBase64Encoded"),
                    config.getString("auth0.ddpKey"), config.getString("auth0.ddpSecret"),
                    config.getString("auth0.mgtKey"), config.getString("auth0.mgtSecret"), config.getString("auth0.mgtApiUrl"),
                    (!config.hasPath("auth0.emailVerificationRequired")) ? true : config.getBoolean("auth0.emailVerificationRequired"));
        }

        return auth0Util;
    }

    public static boolean useAuth0(@NonNull Config config) {
        return (!config.hasPath("auth0.skip") || !config.getBoolean("auth0.skip"));
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
                }
                catch (Exception ex) {
                    throw new RuntimeException("Unable to get access token for audience " + audience, ex);
                }
            }
            else {
                throw new RuntimeException("Auth0 Audience is missing.");
            }
        }
        return token;
    }

    public static class Auth0UserInfo {

        private String email;
        private long expirationTime;
        private boolean emailVerified;

        public Auth0UserInfo(@NonNull Object emailObj, @NonNull Object expirationTimeObj, @NonNull Object emailVerifiedObj) {
            this.email = emailObj.toString();
            this.expirationTime = Long.parseLong(expirationTimeObj.toString());
            this.emailVerified = (boolean) emailVerifiedObj;
        }

        public String getEmail() {
            return email;
        }

        public long getTokenExpiration() {
            return expirationTime;
        }

        public boolean getEmailVerified() {
            return emailVerified;
        }
    }
}
