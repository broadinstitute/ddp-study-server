package org.broadinstitute.lddp.security;

import java.util.Date;
import java.util.Map;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.NonNull;
import org.broadinstitute.lddp.exception.InvalidTokenException;
import org.broadinstitute.lddp.util.Utility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SecurityHelper {
    public static final String CLAIM_ISSUER = "iss";
    public static final String CLAIM_MONITORINGSYSTEM = "monitor";
    public static final String MONITORING_SYSTEM = "google";
    public static final String SIGNER = "org.broadinstitute.kdux";
    public static final String BSP_SIGNER = "https://dsm-dev.datadonationplatform.org/ddp/";
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER = "Bearer ";
    public static final String BASIC = "Basic ";
    private static final Logger logger = LoggerFactory.getLogger(SecurityHelper.class);

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
            builder.withClaim(CLAIM_ISSUER, SIGNER);
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return builder.sign(algorithm);
        } catch (Exception e) {
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
        } catch (Exception e) {
            throw new RuntimeException("Couldn't create token " + e);
        }
    }

    public static String createGpToken(@NonNull String secret, long invalidAfter, @NonNull Map<String, String> claims) {
        try {
            Date dateSoon = new Date(invalidAfter * 1000);

            JWTCreator.Builder builder = JWT.create();
            builder.withIssuer(BSP_SIGNER);
            builder.withExpiresAt(dateSoon);
            if (claims != null) {
                claims.forEach((key, value) -> {
                    builder.withClaim(key, value);
                });
            }
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return builder.sign(algorithm);
        } catch (Exception e) {
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
        } catch (Exception e) {
            // todo arz probably want to catch specific exceptions for
            // validation failure vs. algorithm related stuff
            logger.warn("Security - Error verifying token", e);
        }
        return isValid;
    }

    public static Map<String, Claim> verifyAndGetClaims(@NonNull String secret, @NonNull String token) throws InvalidTokenException {
        return verifyAndGetClaims(secret, token, false);
    }

    private static Map<String, Claim> verifyAndGetClaims(@NonNull String secret, @NonNull String token, boolean checkMonitoringClaim)
            throws InvalidTokenException {

        Map<String, Claim> claimsMap = null;

        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            JWTVerifier verifier = JWT.require(algorithm).build();
            if (checkMonitoringClaim) {
                verifier = JWT.require(algorithm).acceptExpiresAt(Utility.getCurrentEpoch() + 60).build();
            }
            DecodedJWT jwt = verifier.verify(token);
            claimsMap = jwt.getClaims();
        } catch (Exception e) {
            throw new InvalidTokenException("Invalid token", e);
        }

        if (SIGNER.equals(claimsMap.get(CLAIM_ISSUER).asString())) {
            return claimsMap;
        } else {
            throw new InvalidTokenException("Token is not signed by the expected signer.");
        }

    }

    public enum ResultType {
        AUTHENTICATION_ERROR, AUTHORIZATION_ERROR, AUTHORIZED
    }
}
