package org.broadinstitute.dsm.util;

import java.io.UnsupportedEncodingException;
import java.util.Map;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.exception.AuthenticationException;
import org.broadinstitute.dsm.security.Auth0Util;
import org.broadinstitute.lddp.exception.InvalidTokenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;

/**
 * Utility for "before" filter that will halt with a 404
 * if the proper jwt token is not present.
 *
 * <p>We should halt with a 404 instead of a 401/404
 * because we don't want to "leak" the fact
 * that any given route actually exists.
 */
public class JWTRouteFilter {

    public static final String AUTHORIZATION = "Authorization";
    public static final String BEARER = "Bearer";
    // todo arz refactor this with ddp backend core jwt util
    public static final String DDP_ROLES_CLAIM = "org.broadinstitute.ddp.roles";
    private static final Logger logger = LoggerFactory.getLogger(JWTRouteFilter.class);
    private final String auth0Domain;

    /**
     * Create a filter that will only allow access if a
     * bearer auth JWT token is present, signed with the given
     * secret, and optionally includes one or more of the
     * given roles in the roles claim.
     */
    public JWTRouteFilter(String auth0Domain) {
        this.auth0Domain = auth0Domain;
    }

    /**
     * Return true if the request has the appropriate jwt token, false otherwise
     *
     * @throws TokenExpiredException for expired token
     * @throws InvalidTokenException for invalid token
     * @throws AuthenticationException for other authentication issues
     */
    public boolean isAccessAllowed(Request request, boolean isRSA, String secret) {
        boolean isAccessAllowed = false;
        if (request != null) {
            String authHeader = request.headers(AUTHORIZATION);
            if (StringUtils.isNotBlank(authHeader)) {
                String[] parsedAuthHeader = authHeader.split(BEARER);
                if (parsedAuthHeader.length == 2) {
                    String jwtToken = parsedAuthHeader[1].trim();
                    if (StringUtils.isNotBlank(jwtToken)) {
                        Map<String, Claim> verifiedClaims;
                        if (isRSA) {
                            verifiedClaims = Auth0Util.verifyAuth0Token(jwtToken, auth0Domain).getClaims();
                        } else {
                            try {
                                Algorithm algorithm = Algorithm.HMAC256(secret);
                                JWTVerifier verifier = JWT.require(algorithm).build(); //Reusable verifier instance
                                DecodedJWT jwt = verifier.verify(jwtToken);
                                verifiedClaims = jwt.getClaims();
                            } catch (UnsupportedEncodingException e) {
                                throw new AuthenticationException(e);
                            }
                        }
                        if (verifiedClaims != null) {
                            // no role restriction required, just a valid signature
                            isAccessAllowed = true;
                        } else {
                            logger.error("Claims were null, token deosn't have valid signature and access is not allowed");
                        }
                    }
                } else {
                    logger.warn("Header was missing Bearer information (not length 2)");
                }
            } else {
                logger.warn("Header was missing Authorization information");
            }
        }
        return isAccessAllowed;
    }
}
