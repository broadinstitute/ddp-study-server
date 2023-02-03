package org.broadinstitute.dsm.route;

import static spark.Spark.halt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.auth0.jwt.interfaces.Claim;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.entity.ContentType;
import org.apache.http.message.BasicNameValuePair;
import org.broadinstitute.dsm.db.UserSettings;
import org.broadinstitute.dsm.db.dao.user.UserDao;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.dsm.exception.AuthenticationException;
import org.broadinstitute.dsm.model.auth0.Auth0M2MResponse;
import org.broadinstitute.dsm.util.DDPRequestUtil;
import org.broadinstitute.dsm.util.UserUtil;
import org.broadinstitute.lddp.security.Auth0Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

public class AuthenticationRoute implements Route {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationRoute.class);
    public static final String authUserId = "USER_ID";
    private static final String payloadToken = "token";
    private static final String authUserName = "USER_NAME";
    private static final String authUserEmail = "USER_MAIL";
    private static final String userAccessRoles = "USER_ACCESS_ROLE";
    private static final String userSettings = "USER_SETTINGS";
    private static final String clientId = "https://datadonationplatform.org/cid";
    private static final String userId = "https://datadonationplatform.org/uid";
    private static final String tenantDomain = "https://datadonationplatform.org/t";
    private static final String clientIdKey = "client_id";
    private static final String grantTypeKey = "grant_type";
    private static final String clientSecretKey = "client_secret";
    private static final String audienceKey = "audience";
    private static final String api = "/oauth/token";
    private static final String contentType = "application/x-www-form-urlencoded";
    private static final String clientCredentials = "client_credentials";

    private final Auth0Util auth0Util;

    private final UserUtil userUtil;
    private final String auth0Domain;
    private final String clientSecret;
    private final String auth0ClientId;
    private final String auth0MgmntAudience;
    private final String audienceNameSpace;

    public AuthenticationRoute(@NonNull Auth0Util auth0Util, @NonNull UserUtil userUtil, @NonNull String auth0Domain,
                               @NonNull String clientSecret,
                               @NonNull String auth0ClientId, @NonNull String auth0MgmntAudience, @NonNull String audienceNameSpace) {

        this.auth0Util = auth0Util;
        this.userUtil = userUtil;
        this.auth0Domain = auth0Domain;
        this.clientSecret = clientSecret;
        this.auth0ClientId = auth0ClientId;
        this.auth0MgmntAudience = auth0MgmntAudience;
        this.audienceNameSpace = audienceNameSpace;
    }

    @Override
    public Object handle(Request request, Response response) {
        logger.info("Check user...");
        try {
            JsonObject jsonObject = JsonParser.parseString(request.body()).getAsJsonObject();
            String auth0Token = jsonObject.get(payloadToken).getAsString();
            if (StringUtils.isNotBlank(auth0Token)) {
                // checking if Auth0 knows that token
                try {
                    Auth0Util.Auth0UserInfo auth0UserInfo = auth0Util.getAuth0UserInfo(auth0Token, auth0Domain);
                    if (auth0UserInfo != null) {
                        String email = auth0UserInfo.getEmail();
                        logger.info("User (" + email + ") was found ");
                        Gson gson = new Gson();
                        Map<String, String> claims = new HashMap<>();
                        UserDao userDao = new UserDao();
                        UserDto userDto =
                                userDao.getUserByEmail(email).orElseThrow(() -> new RuntimeException("User " + email + " not found!"));
                        if (userDto == null) {
                            userUtil.insertUser(email, email);
                            userDto = userDao.getUserByEmail(email)
                                    .orElseThrow(() -> new RuntimeException("new inserted user " + email + " not found!"));
                            claims.put(userAccessRoles, "user needs roles and groups");
                        } else {
                            String userSetting = gson.toJson(userUtil.getUserAccessRoles(email), ArrayList.class);
                            claims.put(userAccessRoles, userSetting);
                            logger.info(userSetting);
                            claims.put(userSettings, gson.toJson(UserSettings.getUserSettings(email), UserSettings.class));
                        }
                        claims.put(authUserId, String.valueOf(userDto.getId()));
                        claims.put(authUserName, userDto.getName().orElse(""));
                        claims.put(authUserEmail, email);
                        claims = getDSSClaimsFromOriginalToken(auth0Token, auth0Domain, claims);

                        try {
                            String dsmToken =
                                    getNewAuth0TokenWithCustomClaims(claims, clientSecret, auth0ClientId, auth0Domain, auth0MgmntAudience,
                                            audienceNameSpace);
                            if (dsmToken != null) {
                                return new DSMToken(dsmToken);
                            } else {
                                haltWithErrorMsg(401, response, "DSMToken was null! Not authorized user");
                            }
                        } catch (AuthenticationException e) {
                            haltWithErrorMsg(401, response, "DSMToken was null! Not authorized user", e);
                        }
                    } else {
                        haltWithErrorMsg(400, response, "user was null");
                    }
                } catch (AuthenticationException e) {
                    haltWithErrorMsg(400, response, "Problem getting user info from Auth0 token", e);
                }
            } else {
                haltWithErrorMsg(400, response, "There was no token in the payload");
            }
        } catch (JsonSyntaxException e) {
            haltWithErrorMsg(400, response, "The provided JSON in the request was malformed", e);
        }
        return response;
    }

    private Map<String, String> getDSSClaimsFromOriginalToken(String auth0Token, String auth0Domain, Map<String, String> claims) {
        Map<String, Claim> auth0Claims = Auth0Util.verifyAndParseAuth0TokenClaims(auth0Token, auth0Domain);

        if (!auth0Claims.containsKey(tenantDomain) || !auth0Claims.containsKey(clientId) || !auth0Claims.containsKey(userId)) {
            logger.warn("Missing dss claims in auth0 claims, can not authenticate for DSS!");
        }

        if (auth0Claims.containsKey(tenantDomain)) {
            claims.put(tenantDomain, auth0Claims.get(tenantDomain).asString());
        }
        if (auth0Claims.containsKey(clientId)) {
            claims.put(clientId, auth0Claims.get(clientId).asString());
        }
        if (auth0Claims.containsKey(userId)) {
            claims.put(userId, auth0Claims.get(userId).asString());
        }

        return claims;
    }

    private String getNewAuth0TokenWithCustomClaims(Map<String, String> claims, String clientSecret, String clientId, String auth0Domain,
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

    private static class DSMToken {
        private String dsmToken;

        public DSMToken(String token) {
            this.dsmToken = token;
        }
    }

    /**
     * sets the status to the code and the message to the given error message
     */
    public static void haltWithErrorMsg(int responseStatus, Response response, String message) {
        response.type(ContentType.APPLICATION_JSON.getMimeType());
        logger.error(message);
        String errorMsgJson = new Gson().toJson(new Error(message));
        halt(responseStatus, errorMsgJson);
    }


    public static void haltWithErrorMsg(int responseStatus, Response response, String message, Throwable t) {
        if (t != null) {
            logger.error("Authentication Error", t);
        }
        haltWithErrorMsg(responseStatus, response, message);
    }
}
