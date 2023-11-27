package org.broadinstitute.dsm.util;

import static org.apache.http.client.fluent.Request.Get;
import static org.apache.http.client.fluent.Request.Post;

import java.net.URLEncoder;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.Claim;
import com.google.gson.GsonBuilder;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.TrustStrategy;
import org.broadinstitute.dsm.DSMServer;
import org.broadinstitute.dsm.exception.AuthenticationException;
import org.broadinstitute.dsm.security.Auth0Util;
import org.broadinstitute.lddp.exception.InvalidTokenException;
import org.broadinstitute.lddp.security.SecurityHelper;
import spark.Request;

public class SecurityUtil {

    public static final String CLAIM_ISSUER = "iss";
    public static final String USER_ID = "USER_ID";
    public static final String SIGNER = "org.broadinstitute.kdux";

    private static String AUTH0_DOMAIN;
    private static String AUTH0_NAMESPACE;
    private static String AUTH0_SIGNER;

    public static void init(@NonNull String auth0Domain, @NonNull String auth0Namespace, @NonNull String auth0Signer) {
        SecurityUtil.AUTH0_DOMAIN = auth0Domain;
        SecurityUtil.AUTH0_NAMESPACE = auth0Namespace;
        SecurityUtil.AUTH0_SIGNER = auth0Signer;
    }

    public static Map<String, String> createHeader(@NonNull String instanceName, boolean auth0Token) {
        String token = null;
        String secret = null;
        if (auth0Token) {
            token = DSMServer.getAuth0Util().getAccessToken();
        } else {
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

    public static Map<String, String> createHeader(@NonNull String instanceName, boolean auth0Token, Auth0Util auth0Util) {
        String token = null;
        String secret = null;
        if (auth0Token) {
            token = auth0Util.getAccessToken();
        } else {
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

    public static org.apache.http.client.fluent.Request createPostRequestWithHeaderNoToken(@NonNull String uri,
                                                                                           Map<String, String> headers,
                                                                                           Object objectToPost) {
        org.apache.http.client.fluent.Request request = Post(uri);
        if (headers != null) {
            for (Map.Entry<String, String> headerEntry : headers.entrySet()) {
                request = request.addHeader(headerEntry.getKey(), headerEntry.getValue());
            }
        }
        return addBodyToRequest(objectToPost, request, ContentType.APPLICATION_FORM_URLENCODED);
    }

    private static org.apache.http.client.fluent.Request addBodyToRequest(Object objectToPost,
                                                                          org.apache.http.client.fluent.Request request) {
        if (objectToPost != null) {
            String content = null;
            if (!(objectToPost instanceof String)) {
                content = new GsonBuilder().create().toJson(objectToPost);
            } else {
                content = (String) objectToPost;
            }
            request.bodyString(content, ContentType.APPLICATION_JSON);
        }
        return request;
    }

    private static org.apache.http.client.fluent.Request addBodyToRequest(Object objectToPost,
                                                                          org.apache.http.client.fluent.Request request,
                                                                          ContentType contentType) {
        if (objectToPost != null) {
            String content = null;
            if (!(objectToPost instanceof String)) {
                content = createPostData((List<BasicNameValuePair>) objectToPost, contentType);
            } else {
                content = (String) objectToPost;
            }
            request.bodyString(content, contentType);
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

    /**
     * Get user ID from request token
     *
     * @throws TokenExpiredException for expired token
     * @throws InvalidTokenException for invalid token
     * @throws AuthenticationException for other authentication issues
     */
    public static String getUserId(@NonNull Request request) {
        String userId = null;
        Map<String, Claim> claims = getClaims(request);
        if (!claims.isEmpty() && claims.containsKey(AUTH0_NAMESPACE + USER_ID)) {
            userId = claims.get(AUTH0_NAMESPACE + USER_ID).asString();
        }
        return userId;
    }

    private static Map<String, Claim> getClaims(@NonNull Request request) {
        String header = request.headers(JWTRouteFilter.AUTHORIZATION);
        if (StringUtils.isNotBlank(header)) {
            if (header.contains("Bearer ")) {
                String token = header.replaceFirst("Bearer ", "");
                if (StringUtils.isNotBlank(token)) {
                    return SecurityHelper.verifyAndGetClaims(token, AUTH0_DOMAIN, AUTH0_SIGNER);
                }
            }
        }
        return Collections.emptyMap();
    }

    public enum ResultType {
        AUTHENTICATION_ERROR, AUTHORIZATION_ERROR, AUTHORIZED
    }

    private static String createPostData(List<BasicNameValuePair> params, ContentType contentType)  {
        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (NameValuePair pair : params) {
            if (first) {
                first = false;
            } else {
                result.append("&");
            }
            result.append(URLEncoder.encode(pair.getName(), contentType.getCharset()));
            result.append("=");
            result.append(URLEncoder.encode(pair.getValue(), contentType.getCharset()));
        }
        return result.toString();
    }
}
