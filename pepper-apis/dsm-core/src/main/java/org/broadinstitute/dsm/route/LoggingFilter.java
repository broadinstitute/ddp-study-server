package org.broadinstitute.dsm.route;

import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.apache.commons.lang3.StringUtils;
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

    @Override
    public void handle(Request request, Response response) {
        String tokenFromHeader = Utility.getTokenFromHeader(request);
        if (StringUtils.isNotBlank(tokenFromHeader)) {
            try {
                DecodedJWT decodedUnverifiedJWT = JWT.decode(tokenFromHeader);
                Claim userEmailClaim = decodedUnverifiedJWT.getClaim("USER_MAIL");

                if (userEmailClaim != null) {
                    String userEmail = userEmailClaim.asString();
                    if (StringUtils.isNotBlank(userEmail)) {
                        MDC.put(USER_EMAIL, userEmail);
                    }
                }

            } catch (JWTDecodeException e) {
                logger.debug("Could not decode token", e);
            }
        }

        // set the ip  so that log4j can include it
        if (StringUtils.isNotBlank(request.ip())) {
            MDC.put(CLIENT_IP, request.ip());
        }
        logger.info("Accessing " + request.url());
    }
}
