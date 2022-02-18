package org.broadinstitute.dsm.route;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.UserSettings;
import org.broadinstitute.dsm.db.dao.user.UserDao;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.dsm.util.UserUtil;
import org.broadinstitute.lddp.security.Auth0Util;
import org.broadinstitute.lddp.security.SecurityHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

public class AuthenticationRoute implements Route {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationRoute.class);
    public final String authUserId = "USER_ID";
    private final String payloadToken = "token";
    private final String authUserName = "USER_NAME";
    private final String authUserEmail = "USER_MAIL";
    private final String userAccessRoles = "USER_ACCESS_ROLE";
    private final String userSettings = "USER_SETTINGS";

    private final Auth0Util auth0Util;

    private final String jwtSecret;
    private final String cookieSalt;
    private final String cookieName;
    private final UserUtil userUtil;
    private final String environment;

    public AuthenticationRoute(@NonNull Auth0Util auth0Util, @NonNull String jwtSecret, @NonNull String cookieSalt,
                               @NonNull String cookieName, @NonNull UserUtil userUtil, String environment) {
        if (StringUtils.isBlank(jwtSecret) || StringUtils.isBlank(cookieSalt) || StringUtils.isBlank(cookieName)) {
            throw new RuntimeException("Browser security information is missing");
        }
        this.auth0Util = auth0Util;
        this.jwtSecret = jwtSecret;
        this.cookieSalt = cookieSalt;
        this.cookieName = cookieName;
        this.userUtil = userUtil;
        this.environment = environment;
    }

    @Override
    public Object handle(Request request, Response response) throws Exception {
        logger.info("Check user...");
        JsonObject jsonObject = new JsonParser().parse(request.body()).getAsJsonObject();
        String auth0Token = jsonObject.get(payloadToken).getAsString();
        if (StringUtils.isNotBlank(auth0Token)) {
            // checking if Auth0 knows that token
            Auth0Util.Auth0UserInfo auth0UserInfo = auth0Util.getAuth0UserInfo(auth0Token);
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

                long auth0Expiration = auth0UserInfo.getTokenExpiration();
                int cookieAgeInSeconds = new Long(auth0Expiration - new Double(System.currentTimeMillis() / 1000d).intValue()).intValue();

                String jwtToken =
                        new SecurityHelper().createToken(jwtSecret, cookieAgeInSeconds + (System.currentTimeMillis() / 1000) + (60 * 5),
                                claims);

                DSMToken authResponse = new DSMToken(jwtToken);
                return authResponse;
            } else {
                throw new RuntimeException("UserIdentity not found");
            }
        } else {
            throw new RuntimeException("There was no token in the payload");
        }
    }

    private static class DSMToken {
        private String dsmToken;

        public DSMToken(String token) {
            this.dsmToken = token;
        }
    }
}
