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
import org.broadinstitute.dsm.db.dao.settings.UserSettingsDao;
import org.broadinstitute.dsm.db.dao.user.UserDao;
import org.broadinstitute.dsm.db.dto.settings.UserSettingsDto;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.dsm.exception.AuthenticationException;
import org.broadinstitute.dsm.exception.DaoException;
import org.broadinstitute.dsm.model.auth0.Auth0M2MResponse;
import org.broadinstitute.dsm.util.DDPRequestUtil;
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

    private final String auth0Domain;
    private final String clientSecret;
    private final String auth0ClientId;
    private final String auth0MgmntAudience;
    private final String audienceNameSpace;
    private UserDao userDao;
    private UserSettingsDao userSettingsDao;

    public AuthenticationRoute(@NonNull Auth0Util auth0Util, @NonNull String auth0Domain,
                               @NonNull String clientSecret,
                               @NonNull String auth0ClientId, @NonNull String auth0MgmntAudience, @NonNull String audienceNameSpace,
                               UserSettingsDao userSettingsDao) {

        this.auth0Util = auth0Util;
        this.auth0Domain = auth0Domain;
        this.clientSecret = clientSecret;
        this.auth0ClientId = auth0ClientId;
        this.auth0MgmntAudience = auth0MgmntAudience;
        this.audienceNameSpace = audienceNameSpace;
        this.userDao = new UserDao();
        this.userSettingsDao = userSettingsDao;
    }

    @Override
    public Object handle(Request request, Response response) throws DaoException {
        logger.info("Check user...");
        try {
            JsonObject jsonObject = new JsonParser().parse(request.body()).getAsJsonObject();
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
                        UserDto userDto =
                                userDao.getUserByEmail(email).orElseThrow(() -> new RuntimeException("User " + email + " not found!"));
                        Map<String, Claim> auth0Claims = Auth0Util.verifyAndParseAuth0TokenClaims(auth0Token, auth0Domain);
                        if (userDto == null) {
                            throw new RuntimeException("User with email " + email + " not found!");
                        } else {
                            if (StringUtils.isBlank(userDto.getAuth0UserId())) {
                                userDao.updateAuth0UserId(userDto.getUserId(), auth0Claims.get("sub").asString());
                            }
                            String userPermissions = gson.toJson(userDao.getAllUserPermissions(userDto.getUserId()), ArrayList.class);
                            claims.put(userAccessRoles, userPermissions);
                            logger.info(userPermissions);
                            claims.put(userSettings,
                                    gson.toJson(userSettingsDao.get(userDto.getUserId()).orElseThrow(), UserSettingsDto.class));
                        }
                        claims.put(authUserId, String.valueOf(userDto.getUserId()));
                        claims.put(authUserName, userDto.getName().orElse(""));
                        claims.put(authUserEmail, email);
                        claims = getDSSClaimsFromOriginalToken(claims, email, auth0Claims, userDto.getGuid());

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
                        haltWithErrorMsg(400, response, "user info in token was null");
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

    private Map<String, String> getDSSClaimsFromOriginalToken(Map<String, String> claims, String email, Map<String, Claim> auth0Claims,
                                                              String userGuid) {


        if (!auth0Claims.containsKey(tenantDomain) || !auth0Claims.containsKey(clientId) || !auth0Claims.containsKey(userId)) {
            throw new RuntimeException("Missing dss claims in auth0 claims, can not authenticate");
        }
        claims.put(tenantDomain, auth0Claims.get(tenantDomain).asString());
        claims.put(clientId, auth0Claims.get(clientId).asString());
        claims.put(userId, userGuid);

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
