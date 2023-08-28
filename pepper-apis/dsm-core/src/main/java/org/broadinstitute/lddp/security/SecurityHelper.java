package org.broadinstitute.lddp.security;

import java.util.Date;
import java.util.Map;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.Claim;
import lombok.NonNull;
import org.broadinstitute.dsm.exception.AuthenticationException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.security.Auth0Util;
import org.broadinstitute.lddp.exception.InvalidTokenException;
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
            throw new DsmInternalError("Couldn't create token " + e);
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
            claims.forEach((key, value) -> builder.withClaim(key, value));
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return builder.sign(algorithm);
        } catch (Exception e) {
            throw new DsmInternalError("Couldn't create token " + e);
        }
    }

    public static String createTokenWithSigner(@NonNull String secret, long invalidAfter, @NonNull Map<String, String> claims, @NonNull String signer) {
        try {
            Date dateSoon = new Date(invalidAfter * 1000);
            JWTCreator.Builder builder = JWT.create();
            builder.withIssuer(signer);
            builder.withExpiresAt(dateSoon);
            claims.forEach((key, value) -> builder.withClaim(key, value));
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return builder.sign(algorithm);
        } catch (Exception e) {
            throw new DsmInternalError("Couldn't create token " + e);
        }
    }

    /**
     * Verify token and signer, and return  token claims
     *
     * @param token token to verify
     * @param auth0Domain auth0 domain
     * @param auth0Signer token issuer
     * @return a map of claims
     * @throws TokenExpiredException for expired token
     * @throws InvalidTokenException for invalid token
     * @throws AuthenticationException for other authentication issues
     */
    public static Map<String, Claim> verifyAndGetClaims(@NonNull String token, @NonNull String auth0Domain, @NonNull String auth0Signer) {
        Map<String, Claim> claimsMap = Auth0Util.verifyAndParseAuth0TokenClaims(token, auth0Domain);
        if (auth0Signer.equals(claimsMap.get(CLAIM_ISSUER).asString())) {
            return claimsMap;
        } else {
            throw new InvalidTokenException("Token is not signed by the expected signer.");
        }
    }

    public enum ResultType {
        AUTHENTICATION_ERROR, AUTHORIZATION_ERROR, AUTHORIZED
    }
}
