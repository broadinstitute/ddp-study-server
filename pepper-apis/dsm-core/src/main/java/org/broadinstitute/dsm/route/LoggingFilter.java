package org.broadinstitute.dsm.route;

import static spark.Spark.halt;

import java.util.Optional;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.security.Auth0Util;
import org.broadinstitute.lddp.util.Utility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import spark.Filter;
import spark.Request;
import spark.Response;

/**
 * Captures client IP and name of user from token, adding
 * them to MDC for logging.
 */
public class LoggingFilter implements Filter {

    public static final String CLIENT_IP = "CLIENT_IP";

    public static final String USER_EMAIL = "USER_EMAIL";

    private static final Logger logger = LoggerFactory.getLogger(LoggingFilter.class);
    private String auth0Domain;
    private String claimNameSpace;
    private String secret; // this is not static!
    private String issuer = null; // this is not static!
    private boolean secretEncoded = false; // this is not static!

    public LoggingFilter(String auth0Domain, String claimNameSpace, String secret, String issuer, boolean secretEncoded) {
        this.auth0Domain = auth0Domain;
        this.claimNameSpace = claimNameSpace;
        this.secret = secret;
        this.secretEncoded = secretEncoded;
        this.issuer = issuer;
    }

    @Override
    public void handle(Request request, Response response) {
        String tokenFromHeader = Utility.getTokenFromHeader(request);
        if (StringUtils.isNotBlank(tokenFromHeader) && !"null".equals(tokenFromHeader)) {
            Optional<DecodedJWT> maybeDecodedUnverifiedJWT =
                    Auth0Util.verifyAuth0Token(tokenFromHeader, auth0Domain, secret, issuer, secretEncoded);
            maybeDecodedUnverifiedJWT.ifPresentOrElse(decodedJWT -> {
                Claim userEmailClaim = decodedJWT.getClaim(claimNameSpace + "USER_MAIL");

                if (userEmailClaim != null) {
                    String userEmail = userEmailClaim.asString();
                    if (StringUtils.isNotBlank(userEmail)) {
                        MDC.put(USER_EMAIL, userEmail);
                    }
                }
            }, () -> {
                    logger.error("Unable to verify token");
                    halt(401);
                    return;
                });

        }

        // set the ip  so that log4j can include it
        if (StringUtils.isNotBlank(request.ip())) {
            MDC.put(CLIENT_IP, request.ip());
        }
        logger.info("Accessing " + request.url());
    }
}
