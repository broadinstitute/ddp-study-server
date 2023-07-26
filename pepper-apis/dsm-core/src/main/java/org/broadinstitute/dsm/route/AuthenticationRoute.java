package org.broadinstitute.dsm.route;

import static spark.Spark.halt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.entity.ContentType;
import org.broadinstitute.dsm.db.UserSettings;
import org.broadinstitute.dsm.db.dao.user.UserDao;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.dsm.exception.AuthenticationException;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.util.UserUtil;
import org.broadinstitute.dsm.security.Auth0Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

public class AuthenticationRoute implements Route {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationRoute.class);
    private static final String authUserId = "USER_ID";
    private static final String payloadToken = "token";
    private static final String authUserName = "USER_NAME";
    private static final String authUserEmail = "USER_MAIL";
    private static final String userAccessRoles = "USER_ACCESS_ROLE";
    private static final String userSettings = "USER_SETTINGS";

    private final Auth0Util auth0Util;

    private final UserUtil userUtil;
    private final String auth0Domain;
    private final String clientSecret;
    private final String auth0ClientId;
    private final String auth0MgmntAudience;
    private final String audienceNameSpace;

    public AuthenticationRoute(@NonNull Auth0Util auth0Util, @NonNull UserUtil userUtil, @NonNull String auth0Domain,
                               @NonNull String clientSecret, @NonNull String auth0ClientId, @NonNull String auth0MgmntAudience,
                               @NonNull String audienceNameSpace) {
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
            if (StringUtils.isBlank(auth0Token)) {
                haltWithErrorMsg(400, response, "There was no token in the payload");
            }

            // checking if Auth0 knows that token
            try {

                String dsmToken = updateToken(auth0Token);
                return new DSMToken(dsmToken);
            } catch (AuthenticationException e) {
                haltWithErrorMsg(401, response, "DSMToken was null! Not authorized user", e);
            }
        } catch (AuthenticationException e) {
            haltWithErrorMsg(400, response, "Problem getting user info from Auth0 token", e);
        }

       return response;
    }

    private String updateToken(String auth0Token) {
        try {
            Auth0Util.Auth0UserInfo auth0UserInfo = auth0Util.getAuth0UserInfo(auth0Token, auth0Domain);
            String email = auth0UserInfo.getEmail();
            UserDao userDao = new UserDao();
            UserDto userDto = userDao.getUserByEmail(email).orElseThrow(() ->
                    new DSMBadRequestException("User not found: " + email));

            Map<String, String> claims = updateClaims(userDto);

                String dsmToken = auth0Util.getNewAuth0TokenWithCustomClaims(claims, clientSecret, auth0ClientId, auth0Domain,
                        auth0MgmntAudience, audienceNameSpace);
                if (dsmToken == null) {
                    throw new DsmInternalError("Assert: Auth token should not be null");
                }
                return dsmToken;
            } catch (AuthenticationException e) {
                haltWithErrorMsg(401, response, "DSMToken was null! Not authorized user", e);
            }
        } catch (AuthenticationException e) {
            haltWithErrorMsg(400, response, "Problem getting user info from Auth0 token", e);
        }
    }

    private Map<String, String> updateClaims(UserDto userDto) {
        Map<String, String> claims = new HashMap<>();
        try {
            Gson gson = new Gson();
            String email = userDto.getEmail().orElseThrow(() -> new DsmInternalError("User email cannot be null"));
            String userSetting = gson.toJson(userUtil.getUserAccessRoles(email), ArrayList.class);
            claims.put(userAccessRoles, userSetting);
            claims.put(userSettings, gson.toJson(UserSettings.getUserSettings(email), UserSettings.class));
            claims.put(authUserId, String.valueOf(userDto.getId()));
            claims.put(authUserName, userDto.getName().orElse(""));
            claims.put(authUserEmail, email);
        } catch (JsonParseException e) {
            throw new DsmInternalError("Error converting class to JSON", e);
        }
        return claims;
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
