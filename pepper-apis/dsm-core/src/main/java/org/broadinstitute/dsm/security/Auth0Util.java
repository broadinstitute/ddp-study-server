package org.broadinstitute.dsm.security;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.auth0.client.auth.AuthAPI;
import com.auth0.client.mgmt.ManagementAPI;
import com.auth0.json.auth.TokenHolder;
import com.auth0.json.mgmt.users.Identity;
import com.auth0.json.mgmt.users.User;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.RSAKeyProvider;
import com.auth0.jwt.interfaces.Verification;
import com.auth0.net.AuthRequest;
import com.auth0.net.Request;
import lombok.NonNull;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.broadinstitute.dsm.exception.AuthenticationException;
import org.broadinstitute.dsm.model.auth0.Auth0M2MResponse;
import org.broadinstitute.dsm.util.DDPRequestUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Auth0Util {

    private static final Logger logger = LoggerFactory.getLogger(Auth0Util.class);
    private static String account;
    private static final String clientIdKey = "client_id";
    private static final String grantTypeKey = "grant_type";
    private static final String clientSecretKey = "client_secret";
    private static final String audienceKey = "audience";
    private static final String api = "/oauth/token";
    private static final String contentType = "application/x-www-form-urlencoded";
    private static final String clientCredentials = "client_credentials";
    private final String mgtApiUrl;
    private final List<String> connections;
    private AuthAPI ddpAuthApi = null;
    private AuthAPI mgtAuthApi = null;
    private String audience;
    private String token;
    private Long expiresAt;

    public Auth0Util(@NonNull String account, @NonNull List<String> connections, @NonNull String ddpKey, @NonNull String ddpSecret,
                     @NonNull String mgtKey, @NonNull String mgtSecret, @NonNull String mgtApiUrl, String audience) {
        this.account = account;
        this.connections = connections;
        this.ddpAuthApi = new AuthAPI(account, ddpKey, ddpSecret);
        this.mgtAuthApi = new AuthAPI(account, mgtKey, mgtSecret);
        this.mgtApiUrl = mgtApiUrl;
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

    private void verifyUserConnection(@NonNull String userId, @NonNull String email) { //todo pegah check
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
            Optional<DecodedJWT> maybeToken = verifyAuth0Token(auth0Token, auth0Domain);
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

    /**
     * Verifies the given token by checking the signature with the
     * given jwk provider with RSA256 Algorithm
     *
     * @param jwt         the token to verify
     * @param auth0Domain auth0 domain
     * @return a verified, decoded JWT
     */
    public static Optional<DecodedJWT> verifyAuth0Token(String jwt, String auth0Domain) {
        RSAKeyProvider keyProvider = null;
        DecodedJWT validToken = null;
        try {
            JwkProvider jwkProvider = new JwkProviderBuilder(auth0Domain).build();
            keyProvider = RSAKeyProviderFactory.createRSAKeyProviderWithPrivateKeyOnly(jwkProvider);
        } catch (Exception e) {
            logger.warn("Could not verify token {} due to jwk error", jwt);
        }
        if (keyProvider != null) {
            try {
                validToken = JWT.require(Algorithm.RSA256(keyProvider)).acceptLeeway(10).build().verify(jwt);
            } catch (Exception e) {
                logger.warn("Could not verify token {}", jwt, e);
            }
        }
        return Optional.ofNullable(validToken);
    }

    /**
     * Verifies the given token by checking the signature with the
     * given jwk provider and deciding what algorithm to use
     *
     * @param jwt           the token to verify
     * @param auth0Domain   auth0 domain
     * @param secret        secret to be used for HMAC256 Algorithm
     * @param signer        the valid issuer of token
     * @param secretEncoded boolean, true if the secret is base64 encoded
     * @return a verified, decoded JWT
     */
    public static Optional<DecodedJWT> verifyAuth0Token(String jwt, String auth0Domain, String secret, String signer,
                                                        boolean secretEncoded) {
        if (StringUtils.isBlank(secret)) {
            return verifyAuth0Token(jwt, auth0Domain);
        }
        DecodedJWT validToken = null;
        byte[] tempSecret = secret.getBytes();
        if (secretEncoded) {
            tempSecret = Base64.decodeBase64(tempSecret);
        }
        try {
            Algorithm algorithm = Algorithm.HMAC256(tempSecret);
            Verification verification = JWT.require(algorithm);
            if (StringUtils.isNotBlank(signer)) {
                verification.withIssuer(signer);
            }
            JWTVerifier verifier = verification.build();
            validToken = verifier.verify(jwt);

        } catch (Exception e) {
            logger.warn("Could not verify token {}", jwt, e);
        }

        return Optional.ofNullable(validToken);
    }

    public String getNewAuth0TokenWithCustomClaims(Map<String, String> claims, String clientSecret, String clientId, String auth0Domain,
                                                    String auth0Audience, String audienceNameSpace) throws AuthenticationException {
        String requestUrl = "https://" + auth0Domain + api;
        Map<String, String> headers = new HashMap<>();
        headers.put("content-type", contentType);

        List<NameValuePair> requestParams =
                buildRequestParams(clientId, clientCredentials, clientSecret, auth0Audience, claims, audienceNameSpace);
        Auth0M2MResponse response;
        try {
            response = DDPRequestUtil.postRequestWithResponse(Auth0M2MResponse.class, requestUrl, requestParams, "auth0 M2M", headers);
            if (response == null) {
                throw new AuthenticationException("Didn't receive a token from auth0!");
            }
        } catch (Exception e) {
            throw new AuthenticationException("couldn't get response from Auth0 for user " + claims.get("USER_EMAIL"), e);
        }
        if (response.getError() != null) {
            throw new AuthenticationException("Got Auth0 M2M error " + response.getError() + " : " + response.getErrorDescription());
        }
        return response.getAccessToken();
    }

    private List<NameValuePair> buildRequestParams(@NonNull String clientId, @NonNull String grantType, @NonNull String clientSecret,
                                                   @NonNull String audience, Map<String, String> claims, String audienceNameSpace) {
        List<NameValuePair> params = new ArrayList<>();
        for (String key : claims.keySet()) {
            String finalKey = key;
            if (key.indexOf(audienceNameSpace) == -1) {
                finalKey = audienceNameSpace + key;
            }
            params.add(new BasicNameValuePair(finalKey, claims.get(key)));
        }
        params.add(new BasicNameValuePair(clientIdKey, clientId));
        params.add(new BasicNameValuePair(grantTypeKey, grantType));
        params.add(new BasicNameValuePair(clientSecretKey, clientSecret));
        params.add(new BasicNameValuePair(audienceKey, audience));
        return params;
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
