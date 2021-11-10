package org.broadinstitute.lddp.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.gson.Gson;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.lddp.exception.InvalidTokenException;
import org.broadinstitute.lddp.util.Utility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

public class SecurityHelper {
    private static final Logger logger = LoggerFactory.getLogger(SecurityHelper.class);

    public enum ResultType {
        AUTHENTICATION_ERROR, AUTHORIZATION_ERROR, AUTHORIZED
    }

    public static final String BASIC_ADMIN_ROLE = "basicAdmin";

    public static final String CLAIM_ROLES = "org.broadinstitute.kdux.auth.roles";
    public static final String CLAIM_ISSUER = "iss";
    public static final String CLAIM_EXPIRATION = "exp";
    public static final String CLAIM_USER = "user";
    public static final String CLAIM_FIRSTNAME = "firstName";
    public static final String CLAIM_LASTNAME = "lastName";
    public static final String CLAIM_PARTICIPANT_ID = "participantId";
    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_MONITORINGSYSTEM = "monitor";

    public static final String CLAIM_RECAPTCHA = "recaptchaAccess";
    public static final String CLAIM_ACCOUNT = "accountAccess";

    public static final String MONITORING_SYSTEM = "google";
    public static final String SIGNER = "org.broadinstitute.kdux";

    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER = "Bearer ";
    public static final String BASIC = "Basic ";

    /**
     * Creates a new jwt token with the given roles
     *
     * @param secret       the shared secret
     * @param invalidAfter utc unix time after which the token expires
     * @param username     the name of the user
     * @param roles        list of role names assigned to the user
     */
    public static String createTokenWithRoles(@NonNull String secret, long invalidAfter, @NonNull String username, @NonNull Collection<String> roles) {
        try {
            Date dateSoon = new Date(invalidAfter * 1000);
            JWTCreator.Builder builder = JWT.create();
            builder.withExpiresAt(dateSoon);
            builder.withClaim(CLAIM_ROLES, new Gson().toJson(roles, ArrayList.class));
            builder.withClaim(CLAIM_USER, username);
            builder.withClaim(CLAIM_ISSUER, SIGNER);
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return builder.sign(algorithm);
        }
        catch (Exception e) {
            throw new RuntimeException("Couldn't create token " + e);
        }
    }

    /**
     * Creates jwt token with a few user-related claims
     *
     * @param secret       the shared secret
     * @param invalidAfter utc unix time after which the token expires
     * @param recaptchaOk  set to true if user has recaptcha level
     *                     of "security".  This bit is used to challenge the user
     *                     with recaptcha if they are trying to read more sensitive
     *                     information like fullname and birthdate on release/consent
     *                     survey.
     * @param accountOk    set to true if the user has been authenticated with an account like Auth0 successfully
     */
    public static String createAccessToken(@NonNull String secret, long invalidAfter, String firstName, String lastName, String participantId,
                                           String email, boolean recaptchaOk, boolean accountOk) {
        try {
            Date dateSoon = new Date(invalidAfter * 1000);
            JWTCreator.Builder builder = JWT.create();
            builder.withExpiresAt(dateSoon);
            builder.withClaim(CLAIM_FIRSTNAME, firstName);
            builder.withClaim(CLAIM_LASTNAME, lastName);
            if (participantId != null) {
                builder.withClaim(CLAIM_PARTICIPANT_ID, participantId);
            }
            builder.withClaim(CLAIM_EMAIL, email);
            builder.withClaim(CLAIM_ISSUER, SIGNER);
            if (recaptchaOk) {
                builder.withClaim(CLAIM_RECAPTCHA, recaptchaOk);
            }
            if (accountOk) {
                builder.withClaim(CLAIM_ACCOUNT, accountOk);
            }
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return builder.sign(algorithm);
        }
        catch (Exception e) {
            throw new RuntimeException("Couldn't create token " + e);
        }
    }

    public static String createAccessTokenAndCookie(@NonNull spark.Response response, @NonNull String secret, @NonNull String salt, @NonNull String cookieName,
                                                    long invalidAfter, String firstName, String lastName, String participantId, String email,
                                                    boolean recaptchaOk, boolean accountOk) throws Exception {
        String jwtToken = SecurityHelper.createAccessToken(secret, invalidAfter, firstName, lastName, participantId, email, recaptchaOk, accountOk);

        org.broadinstitute.lddp.security.CookieUtil cookieUtil = new CookieUtil();
        long durationInSeconds = invalidAfter - Utility.getCurrentEpoch();
        Cookie secureCookie = cookieUtil.createSecureCookieForToken(cookieName, new Long(durationInSeconds).intValue(), jwtToken, salt.getBytes());
        secureCookie.setPath("/");
        secureCookie.setSecure(false);
        response.raw().addCookie(secureCookie);

        return jwtToken;
    }

    /**
     * Creates jwt token for a monitoring application
     *
     * @param secret           the shared secret
     * @param monitoringSystem name of the monitoring system using this token
     */
    public static String createMonitoringToken(@NonNull String secret, String monitoringSystem) {
        try {
            JWTCreator.Builder builder = JWT.create();
            builder.withClaim(CLAIM_MONITORINGSYSTEM, monitoringSystem);
//            builder.withClaim(CLAIM_EXPIRATION, 0);
            builder.withClaim(CLAIM_ISSUER, SIGNER);
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return builder.sign(algorithm);
        }
        catch (Exception e) {
            throw new RuntimeException("Couldn't create token " + e);
        }
    }

    /**
     * Creates a new jwt token with the given claims (auth0 app metadata)
     *
     * @param secret       the shared secret
     * @param invalidAfter utc unix time after which the token expires
     * @param claims       list of claims (e.g., auth0 user information (app metadata, email))
     */
    public static String createToken(@NonNull String secret, long invalidAfter, @NonNull Map<String, String> claims) {
        try {
            Date dateSoon = new Date(invalidAfter * 1000);

            JWTCreator.Builder builder = JWT.create();
            builder.withIssuer(SIGNER);
            builder.withExpiresAt(dateSoon);
            if (claims != null) {
                claims.forEach((key, value) -> {
                    builder.withClaim(key, value);
                });
            }
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return builder.sign(algorithm);
        }
        catch (Exception e) {
            throw new RuntimeException("Couldn't create token " + e);
        }
    }

    public static boolean validToken(@NonNull String secret, @NonNull String token) {
        boolean isValid = false;
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            JWTVerifier verifier = JWT.require(algorithm).build(); //Reusable verifier instance
            DecodedJWT jwt = verifier.verify(token);
            isValid = true;
        }
        catch (Exception e) {
            // todo arz probably want to catch specific exceptions for
            // validation failure vs. algorithm related stuff
            logger.warn("Security - Error verifying token", e);
        }
        return isValid;
    }

    public static boolean hasRole(@NonNull String secret, @NonNull String token, @NonNull String roleName) throws InvalidTokenException {
        boolean hasRole = false;
        Map<String, Claim> claims = verifyAndGetClaims(secret, token);
        Object rolesObj = new Gson().fromJson(claims.get(CLAIM_ROLES).asString(), ArrayList.class) ;
        if (rolesObj != null && rolesObj instanceof Collection) {
            hasRole = ((Collection) claims.get(CLAIM_ROLES)).contains(roleName);
        }
        return hasRole;
    }

    public static boolean hasValidSurveyAuthorization(String secret, String token, boolean isAnonymous, String altPid, boolean requiresRecaptcha, boolean accountNeeded) {
        Map<String, Claim> claims = verifyAndGetClaims(secret, token);

        if (!isAnonymous) {
            String tokenAltPid = getAltPidFromToken(claims);

            if ((tokenAltPid == null) || (!(altPid.equals(tokenAltPid)))) {
                return false;
            }
        }

        if ((requiresRecaptcha) && (!hasEnabledClaim(claims, CLAIM_RECAPTCHA))) {
            return false;
        }

        if ((accountNeeded) && (!hasEnabledClaim(claims, CLAIM_ACCOUNT))) {
            return false;
        }

        return true;
    }

    public static boolean hasValidPdfAuthorization(String secret, String token, String altPid) {
        Map<String, Claim> claims = verifyAndGetClaims(secret, token);

        String tokenAltPid = getAltPidFromToken(claims);
        if ((tokenAltPid == null) || (!(altPid.equals(tokenAltPid)))) {
            return false;
        }

        //only allowed if you are using accounts!
        if (!hasEnabledClaim(claims, CLAIM_ACCOUNT)) {
            return false;
        }

        return true;
    }

    public static boolean hasEnabledClaim(Map<String, Claim> claims, String claimName) {
        boolean hasEnabledClaim = false;
        Object claim = claims.get(claimName).asBoolean();
        if (claim != null) {
            hasEnabledClaim = (Boolean) claim;
        }
        return hasEnabledClaim;
    }

    public static String getAltPidForAccountFromToken(Map<String, Claim> claims) {
        if (!hasEnabledClaim(claims, CLAIM_ACCOUNT)) {
            throw new InvalidTokenException("accountAccess claim must be true");
        }

        String altPid = null;
        Object altPidObj = claims.get(CLAIM_PARTICIPANT_ID).asString();
        if (altPidObj != null) {
            altPid = (String) altPidObj;
        }

        if (StringUtils.isBlank(altPid)) {
            throw new InvalidTokenException("participantId claim is missing or empty");
        }

        return altPid;
    }

    private static String getAltPidFromToken(Map<String, Claim> claims) {
        String altPid = null;
        Object altPidObj = claims.get(CLAIM_PARTICIPANT_ID).asString();
        if (altPidObj != null) {
            altPid = (String) altPidObj;
        }
        return altPid;
    }

    public static Map<String, Claim> verifyAndGetClaims(@NonNull String secret, @NonNull String token) throws InvalidTokenException {
        return verifyAndGetClaims(secret, token, false);
    }

    public static Map<String, Claim> verifyAndGetClaims(@NonNull String secret, @NonNull String token, boolean checkMonitoringClaim) throws InvalidTokenException {

        Map<String, Claim> claimsMap = null;

        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            JWTVerifier verifier = JWT.require(algorithm).build();
            if (checkMonitoringClaim) {
                verifier = JWT.require(algorithm).acceptExpiresAt(Utility.getCurrentEpoch() + 60).build();
            }
            DecodedJWT jwt = verifier.verify(token);
            claimsMap = jwt.getClaims();
        }
        catch (Exception e) {
            throw new InvalidTokenException("Invalid token", e);
        }

        if (SIGNER.equals(claimsMap.get(CLAIM_ISSUER).asString())) {
            return claimsMap;
        }
        else {
            throw new InvalidTokenException("Token is not signed by the expected signer.");
        }
    }

    public static boolean verifyNonUIToken(String secret, @NonNull String token, boolean checkMonitoringClaim) {
        boolean isValid = false;
        if (!StringUtils.isBlank(secret)) {
            try {
                Map<String, Claim> claims = verifyAndGetClaims(secret, token, checkMonitoringClaim);
                if (checkMonitoringClaim) {
                    if (MONITORING_SYSTEM.equals(claims.get(SecurityHelper.CLAIM_MONITORINGSYSTEM).asString())) {
                        isValid = true;
                    }
                }
                else {
                    isValid = true;
                }
            }
            catch (Exception e) {
                logger.error("Security - Error verifying non-UI token", e);
            }
        }
        else {
            logger.error("Security - Unable to determine secret for non-UI token.");
        }
        return isValid;
    }
}
