package org.broadinstitute.dsm.util;

import com.auth0.jwt.interfaces.Claim;
import com.google.gson.GsonBuilder;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.TrustStrategy;
import org.broadinstitute.ddp.security.Auth0Util;
import org.broadinstitute.ddp.security.SecurityHelper;
import org.broadinstitute.dsm.DSMServer;
import spark.Request;

import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

import static org.apache.http.client.fluent.Request.Get;
import static org.apache.http.client.fluent.Request.Post;

public class SecurityUtil {

    public static final String CLAIM_ISSUER = "iss";
    public static final String SIGNER = "org.broadinstitute.kdux";

    private static String secret;

    public SecurityUtil(@NonNull String secret){
        this.secret = secret;
    }

    public enum ResultType {
        AUTHENTICATION_ERROR, AUTHORIZATION_ERROR, AUTHORIZED
    }

    /**
     * Header for ddp communication
     *
     * @param instanceName
     * @param auth0Token
     * @return
     */
    public static Map<String, String> createHeader(@NonNull String instanceName, boolean auth0Token) {
        String token = null;
        String secret = null;
        if (auth0Token) {
            token = DSMServer.getAuth0Util().getAccessToken();
        }
        else {
            secret = DSMServer.getDDPTokenSecret(instanceName);
        }
        if (StringUtils.isBlank(token) && StringUtils.isNotBlank(secret)) {
            long invalidAfter = 300 + (System.currentTimeMillis() / 1000);
            Map<String, String> claims = new HashMap<>();
            claims.put(CLAIM_ISSUER, SIGNER);
            token = SecurityHelper.createToken(secret, invalidAfter, claims);
        }
        if (StringUtils.isBlank(token)) {
            throw new RuntimeException("No token available for " + instanceName);
        }
        return createHeaderWithBearer(token);
    }

    public static Map<String, String> createHeaderWithBearer(@NonNull String bearer) {
        Map<String, String> authHeaders = new HashMap<>();
        authHeaders.put("Authorization", "Bearer " + bearer);
        return authHeaders;
    }

    /**
     * GET Request with header for ddp communication
     *
     * @param requestString
     * @param instanceName
     * @param auth0Token
     * @return
     */
    public static org.apache.http.client.fluent.Request createGetRequestWithHeader(@NonNull String requestString,
                                                                                   @NonNull String instanceName,
                                                                                   boolean auth0Token) {
        org.apache.http.client.fluent.Request request = Get(requestString);
        Map<String, String> headers = createHeader(instanceName, auth0Token);
        if (headers != null) {
            for (Map.Entry<String, String> headerEntry : headers.entrySet()) {
                request = request.addHeader(headerEntry.getKey(), headerEntry.getValue());
            }
        }
        return request;
    }

    public static org.apache.http.client.fluent.Request createGetRequestNoToken(@NonNull String requestString) {
        org.apache.http.client.fluent.Request request = Get(requestString);
        return request;
    }

    /**
     * POST Request with header for ddp communication
     *
     * @param requestString
     * @param instanceName
     * @param auth0Token
     * @param objectToPost
     * @return
     */
    public static org.apache.http.client.fluent.Request createPostRequestWithHeader(@NonNull String requestString,
                                                                                    @NonNull String instanceName,
                                                                                    boolean auth0Token,
                                                                                    Object objectToPost) {
        org.apache.http.client.fluent.Request request = Post(requestString);
        Map<String, String> headers = createHeader(instanceName, auth0Token);
        if (headers != null) {
            for (Map.Entry<String, String> headerEntry : headers.entrySet()) {
                request = request.addHeader(headerEntry.getKey(), headerEntry.getValue());
            }
        }
        return addBodyToRequest(objectToPost, request);
    }

    public static org.apache.http.client.fluent.Request createPostRequestWithHeader(@NonNull String requestString,
                                                                                    @NonNull String bearer,
                                                                                    Object objectToPost) {
        org.apache.http.client.fluent.Request request = Post(requestString);
        Map<String, String> headers = createHeaderWithBearer(bearer);
        if (headers != null) {
            for (Map.Entry<String, String> headerEntry : headers.entrySet()) {
                request = request.addHeader(headerEntry.getKey(), headerEntry.getValue());
            }
        }
        return addBodyToRequest(objectToPost, request);
    }

    private static org.apache.http.client.fluent.Request addBodyToRequest(Object objectToPost, org.apache.http.client.fluent.Request request) {
        if (objectToPost != null) {
            String content = null;
            if (!(objectToPost instanceof String)) {
                content = new GsonBuilder().create().toJson(objectToPost);
            }
            else {
                content = (String) objectToPost;
            }
            request.bodyString(content, ContentType.APPLICATION_JSON);
        }
        return request;
    }

    public static HttpClient buildHttpClient() throws Exception {
        SSLContextBuilder builder = new SSLContextBuilder();
        builder.loadTrustMaterial(null, new TrustStrategy() {
            @Override
            public boolean isTrusted(X509Certificate[] chain,
                                     String authType) {
                return true;

            }
        });

        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(builder.build());
        return HttpClients.custom().setSSLSocketFactory(sslsf).build();
    }

    public static String getUserId(@NonNull Request request) {
        String userId = null;
        Map<String, Claim> claims = getClaims(request);
        if (claims != null && !claims.isEmpty() && claims.containsKey("USER_ID")) {
            Object userIdObj = claims.get("USER_ID").asString();
            if (userIdObj != null) {
                userId = (String) userIdObj;
            }
        }
        return userId;
    }

    private static Map<String, Claim> getClaims(@NonNull Request request) {
        String header = request.headers(JWTRouteFilter.AUTHORIZATION);
        if (StringUtils.isNotBlank(header)) {
            if (header.contains("Bearer ")) {
                String token = header.replaceFirst("Bearer ", "");
                if (StringUtils.isNotBlank(token)) {
                    return SecurityHelper.verifyAndGetClaims(secret, token);
                }
            }
        }
        return null;
    }

    public static org.apache.http.client.fluent.Request createPostRequestWithHeader(@NonNull String requestString,
                                                                                    @NonNull String instanceName,
                                                                                    boolean auth0Token,
                                                                                    Object objectToPost, Auth0Util auth0Util) {
        org.apache.http.client.fluent.Request request = Post(requestString);
        Map<String, String> headers = createHeader(instanceName, auth0Token, auth0Util);
        if (headers != null) {
            for (Map.Entry<String, String> headerEntry : headers.entrySet()) {
                request = request.addHeader(headerEntry.getKey(), headerEntry.getValue());
            }
        }
        return addBodyToRequest(objectToPost, request);
    }
    public static Map<String, String> createHeader(@NonNull String instanceName, boolean auth0Token, Auth0Util auth0Util) {
        String token = null;
        String secret = null;
        if (auth0Token) {
            token =auth0Util.getAccessToken();
        }
        else {
            secret = DSMServer.getDDPTokenSecret(instanceName);
        }
        if (StringUtils.isBlank(token) && StringUtils.isNotBlank(secret)) {
            long invalidAfter = 300 + (System.currentTimeMillis() / 1000);
            Map<String, String> claims = new HashMap<>();
            claims.put(CLAIM_ISSUER, SIGNER);
            token = SecurityHelper.createToken(secret, invalidAfter, claims);
        }
        if (StringUtils.isBlank(token)) {
            throw new RuntimeException("No token available for " + instanceName);
        }
        return createHeaderWithBearer(token);
    }
}
