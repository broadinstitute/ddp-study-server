package org.broadinstitute.dsm.route;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.UserSettings;
import org.broadinstitute.dsm.db.dao.user.UserDao;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.dsm.model.auth0.Auth0M2MRequest;
import org.broadinstitute.dsm.model.auth0.Auth0M2MResponse;
import org.broadinstitute.dsm.util.DDPRequestUtil;
import org.broadinstitute.dsm.util.UserUtil;
import org.broadinstitute.lddp.security.Auth0Util;
import org.broadinstitute.lddp.security.SecurityHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class AuthenticationRoute implements Route {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationRoute.class);
    public final String authUserId = "USER_ID";
    private final String payloadToken = "token";
    private final String authUserName = "USER_NAME";
    private final String authUserEmail = "USER_MAIL";
    private final String userAccessRoles = "USER_ACCESS_ROLE";
    private final String userSettings = "USER_SETTINGS";
    private final String clientId = "https://datadonationplatform.org/cid";
    private final String userId = "https://datadonationplatform.org/uid";
    private final String tenantDomain = "https://datadonationplatform.org/t";


    private final Auth0Util auth0Util;

    private final String jwtSecret;
    private final String cookieSalt;
    private final String cookieName;
    private final UserUtil userUtil;
    private final String environment;
    private final String auth0Domain;
    private final String clientSecret;
    private final String auth0ClientId;
    private final String auth0MgmntAudience;
    private final String audienceNameSpace;

    public AuthenticationRoute(@NonNull Auth0Util auth0Util, @NonNull String jwtSecret, @NonNull String cookieSalt,
                               @NonNull String cookieName, @NonNull UserUtil userUtil, String environment, @NonNull String auth0Domain,
                               @NonNull String clientSecret, @NonNull String auth0ClientId, @NonNull String auth0MgmntAudience, @NonNull String audienceNameSpace) {
        if (StringUtils.isBlank(jwtSecret) || StringUtils.isBlank(cookieSalt) || StringUtils.isBlank(cookieName)) {
            throw new RuntimeException("Browser security information is missing");
        }
        this.auth0Util = auth0Util;
        this.jwtSecret = jwtSecret;
        this.cookieSalt = cookieSalt;
        this.cookieName = cookieName;
        this.userUtil = userUtil;
        this.environment = environment;
        this.auth0Domain = auth0Domain;
        this.clientSecret = clientSecret;
        this.auth0ClientId = auth0ClientId;
        this.auth0MgmntAudience = auth0MgmntAudience;
        this.audienceNameSpace = audienceNameSpace;
    }

    @Override
    public Object handle(Request request, Response response) throws Exception {
        logger.info("Check user...");
        JsonObject jsonObject = new JsonParser().parse(request.body()).getAsJsonObject();
        String auth0Token = jsonObject.get(payloadToken).getAsString();
        if (StringUtils.isNotBlank(auth0Token)) {
            // checking if Auth0 knows that token
            Auth0Util.Auth0UserInfo auth0UserInfo = auth0Util.getAuth0UserInfo(auth0Token, auth0Domain);
            if (auth0UserInfo != null) {
                String email = auth0UserInfo.getEmail();
                logger.info("User (" + email + ") was found ");
                Gson gson = new Gson();
                Map<String, String> claims = new HashMap<>();
                UserDao userDao = new UserDao();
                UserDto userDto = userDao.getUserByEmail(email).orElseThrow();
                if (userDto == null) {
                    userUtil.insertUser(email, email);
                    userDto = userDao.getUserByEmail(email).orElseThrow();
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
                auth0Util.getClaimValue(auth0Token, tenantDomain, auth0Domain).ifPresentOrElse(claim -> claims.put(tenantDomain, claim.asString()),
                        () -> {
                            throw new RuntimeException("Missing " + tenantDomain + " in auth0 claims");
                        });
                auth0Util.getClaimValue(auth0Token, clientId, auth0Domain).ifPresentOrElse(claim -> claims.put(clientId, claim.asString()),
                        () -> {
                            throw new RuntimeException("Missing " + clientId + " in auth0 claims");
                        });
                auth0Util.getClaimValue(auth0Token, userId, auth0Domain).ifPresentOrElse(claim -> claims.put(userId, claim.asString()),
                        () -> {
                            throw new RuntimeException("Missing " + userId + " in auth0 claims");
                        });

                String dsmToken = getNewAuth0TokenWithCustomClaims(claims, clientSecret, auth0ClientId, auth0Domain, auth0MgmntAudience, audienceNameSpace);
                if (dsmToken != null) {
                    return new DSMToken(dsmToken);
                } else{
                    throw new RuntimeException("Not authorized user");
                }
            } else {
                throw new RuntimeException("UserIdentity not found");
            }
        } else {
            throw new RuntimeException("There was no token in the payload");
        }
    }

    private String getNewAuth0TokenWithCustomClaims(Map<String, String> claims, String clientSecret, String clientId, String auth0Domain, String auth0Audience, String audienceNameSpace) {
        String api = "/oauth/token";
        String contentType = "application/x-www-form-urlencoded";
        String clientCredentials = "client_credentials";

        String sendRequest = "https://" + auth0Domain + api;
        Map<String, String> headers = new HashMap<>();
        headers.put("content-type", contentType);

        Auth0M2MRequest requestModel = new Auth0M2MRequest(clientId, clientCredentials, clientSecret, auth0Audience, claims, audienceNameSpace);

        Auth0M2MResponse response;
        try {
            response = DDPRequestUtil.postRequestWithResponse(Auth0M2MResponse.class, sendRequest, requestModel.getParams(), "auth0 M2M", headers);
        }
        catch (Exception e) {
            throw new RuntimeException("couldn't get response from Auth0 for user " + claims.get("USER_EMAIL"), e);
        }
        return response.getAccessToken();
    }

    private static class DSMToken {
        private String dsmToken;

        public DSMToken(String token) {
            this.dsmToken = token;
        }
    }
}
