package org.broadinstitute.dsm.route;

import static spark.Spark.halt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.auth0.jwt.exceptions.TokenExpiredException;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.entity.ContentType;
import org.broadinstitute.dsm.db.UserSettings;
import org.broadinstitute.dsm.db.dao.user.UserDao;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.dsm.exception.AuthenticationException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.security.Auth0Util;
import org.broadinstitute.dsm.util.UserUtil;
import org.broadinstitute.lddp.exception.InvalidTokenException;
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

    private final String auth0Domain;
    private final String clientSecret;
    private final String auth0ClientId;
    private final String auth0MgmntAudience;
    private final String audienceNameSpace;

    public AuthenticationRoute(@NonNull Auth0Util auth0Util, @NonNull String auth0Domain,
                               @NonNull String clientSecret, @NonNull String auth0ClientId, @NonNull String auth0MgmntAudience,
                               @NonNull String audienceNameSpace) {
        this.auth0Util = auth0Util;
        this.auth0Domain = auth0Domain;
        this.clientSecret = clientSecret;
        this.auth0ClientId = auth0ClientId;
        this.auth0MgmntAudience = auth0MgmntAudience;
        this.audienceNameSpace = audienceNameSpace;
    }

    @Override
    public Object handle(Request request, Response response) {
        try {
            JsonObject jsonObject = JsonParser.parseString(request.body()).getAsJsonObject();
            String auth0Token = jsonObject.get(payloadToken).getAsString();
            if (StringUtils.isBlank(auth0Token)) {
                haltWithErrorMsg(401, response, "There was no Auth0 token in the payload");
            }
            return new DSMToken(updateToken(auth0Token));
        } catch (JsonParseException e) {
            haltWithErrorMsg(401, response, "Unable to get Auth0 token from request", e);
        }
        // other exceptions are handled via Spark
        return response;
    }

    /**
     * Verify an Auth0 token, authenticate user, and return a token updated with claims
     *
     * @param auth0Token token to verify
     * @return updated token
     * @throws TokenExpiredException for expired token
     * @throws InvalidTokenException for invalid token
     * @throws AuthenticationException for other authentication issues
     */
    private String updateToken(String auth0Token) {
        String email = auth0Util.getAuth0User(auth0Token, auth0Domain);
        logger.info("Authenticating user {}", email);
        UserDao userDao = new UserDao();
        UserDto userDto = userDao.getUserByEmail(email).orElseThrow(() ->
                new AuthenticationException("User not found: " + email));

        Map<String, String> claims = updateClaims(userDto);
        String dsmToken = auth0Util.getAuth0TokenWithCustomClaims(claims, clientSecret, auth0ClientId, auth0Domain,
                auth0MgmntAudience, audienceNameSpace);
        if (dsmToken == null) {
            throw new DsmInternalError("Assert: Auth token should not be null");
        }
        return dsmToken;
    }

    private Map<String, String> updateClaims(UserDto userDto) {
        Map<String, String> claims = new HashMap<>();
        try {
            Gson gson = new Gson();
            String email = userDto.getEmail().orElseThrow(() -> new DsmInternalError("User email cannot be null"));
            String roles = gson.toJson(UserUtil.getUserAccessRoles(email), ArrayList.class);
            claims.put(userAccessRoles, roles);
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
        private final String dsmToken;

        public DSMToken(String token) {
            this.dsmToken = token;
        }
    }

    /**
     * sets the status to the code and the message to the given error message
     */
    public static void haltWithErrorMsg(int responseStatus, Response response, String message) {
        response.type(ContentType.APPLICATION_JSON.getMimeType());
        // TODO: this is currently called for bad request status. Do we want to log that at error level?
        //  Or perhaps we could use the return status to determine the log level? -DC
        logger.error(message);
        String errorMsgJson = new Gson().toJson(new Error(message));
        halt(responseStatus, errorMsgJson);
    }

    public static void haltWithErrorMsg(int responseStatus, Response response, String message, Throwable t) {
        if (t != null) {
            // TODO: this is currently called for bad request status. Do we want to log that at error level?
            //  Or perhaps we could use the return status to determine the log level? -DC
            logger.error("Authentication Error", t);
        }
        haltWithErrorMsg(responseStatus, response, message);
    }
}
