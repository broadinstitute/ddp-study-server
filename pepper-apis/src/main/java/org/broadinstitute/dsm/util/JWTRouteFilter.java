package org.broadinstitute.dsm.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;

/**
 * Utility for "before" filter that will halt with a 404
 * if the proper jwt token is not present.
 * <p>
 * We should halt with a 404 instead of a 401/404
 * because we don't want to "leak" the fact
 * that any given route actually exists.
 */
public class JWTRouteFilter {

    private static final Logger logger = LoggerFactory.getLogger(JWTRouteFilter.class);

    public static final String AUTHORIZATION = "Authorization";
    public static final String BEARER = "Bearer";

    private final String jwtSecret;

    // todo arz refactor this with ddp backend core jwt util
    public static final String DDP_ROLES_CLAIM = "org.broadinstitute.ddp.roles";

    private final Collection<String> expectedRoles = new HashSet<>();

    /**
     * Create a filter that will only allow access if a
     * bearer auth JWT token is present, signed with the given
     * secret, and optionally includes one or more of the
     * given roles in the roles claim.
     *
     * @param jwtSecret    the shared secret used to sign the token
     * @param allowedRoles If given, at least one of the roles in the list
     *                     must be present in the roles claim in the token.
     *                     If empty or null, the roles claim is not checked.
     */
    public JWTRouteFilter(String jwtSecret, Collection<String> allowedRoles) {
        if (StringUtils.isBlank(jwtSecret)) {
            throw new IllegalArgumentException("jwtSecret is required");
        }
        this.jwtSecret = jwtSecret;
        if (allowedRoles != null && !allowedRoles.isEmpty()) {
            this.expectedRoles.addAll(allowedRoles);
        }

    }

    /**
     * Returns true if the request has the appropriate
     * jwt token, false o'wise
     */
    public boolean isAccessAllowed(Request request) {
        boolean isAccessAllowed = false;
        if (request != null) {
            String authHeader = request.headers(AUTHORIZATION);
            if (StringUtils.isNotBlank(authHeader)) {
                String[] parsedAuthHeader = authHeader.split(BEARER);
                if (parsedAuthHeader != null) {
                    if (parsedAuthHeader.length == 2) {
                        String jwtToken = parsedAuthHeader[1].trim();
                        if (StringUtils.isNotBlank(jwtToken)) {
                            try {
                                Algorithm algorithm = Algorithm.HMAC256(jwtSecret);
                                JWTVerifier verifier = JWT.require(algorithm).build(); //Reusable verifier instance
                                DecodedJWT jwt = verifier.verify(jwtToken);

                                Map<String, Claim> verifiedClaims = jwt.getClaims();
                                if (verifiedClaims != null) {
                                    if (!expectedRoles.isEmpty()) {
                                        if (verifiedClaims.containsKey(DDP_ROLES_CLAIM)) {
                                            Object rolesObj = verifiedClaims.get(DDP_ROLES_CLAIM);
                                            if (rolesObj != null && rolesObj instanceof Collection) {
                                                Collection rolesInToken = (Collection) rolesObj;
                                                for (String expectedRole : expectedRoles) {
                                                    if (rolesInToken.contains(expectedRole)) {
                                                        // token has at least one of the request roles, so allow access
                                                        isAccessAllowed = true;
                                                        break;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    else {
                                        // no role restriction required, just a valid signature
                                        isAccessAllowed = true;
                                    }
                                }
                            }
                            catch (Exception e) {
                                logger.error("Invalid token: " + jwtToken, e);
                            }
                        }
                    }
                }
            }
        }
        return isAccessAllowed;
    }
}
