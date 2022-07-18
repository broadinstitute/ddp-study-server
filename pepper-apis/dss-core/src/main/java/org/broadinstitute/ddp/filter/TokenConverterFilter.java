package org.broadinstitute.ddp.filter;

import static org.broadinstitute.ddp.constants.RouteConstants.Header.AUTHORIZATION;

import com.auth0.jwt.exceptions.TokenExpiredException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.security.DDPAuth;
import org.broadinstitute.ddp.security.JWTConverter;
import org.broadinstitute.ddp.util.ResponseUtil;

import spark.Filter;
import spark.Request;
import spark.Response;

/**
 * Verifies the integrity of the JWT and adds
 * a {@link org.broadinstitute.ddp.security.DDPAuth ddp auth object} as a request
 * {@link #DDP_TOKEN attribute} so that downstream processing
 * can read token values without having to re-verify.
 */
@Slf4j
@AllArgsConstructor
public class TokenConverterFilter implements Filter {
    public static final String DDP_TOKEN = "DDP_AUTH";
    private final JWTConverter jwtConverter;

    @Override
    public void handle(Request request, Response response) {
        try {
            DDPAuth ddpAuth = jwtConverter.convertJWTFromHeader(request.headers(AUTHORIZATION), true);
            request.attribute(DDP_TOKEN, ddpAuth);
        } catch (TokenExpiredException e) {
            log.warn("Found expired token for request", e);

            response.header("WWW-Authenticate", "Bearer error=\"invalid_token\", error_description=\"The access token expired\"");
            var error = new ApiError(ErrorCodes.AUTH_CANNOT_BE_DETERMINED, "The access token has expired");
            throw ResponseUtil.haltError(response, HttpStatus.SC_UNAUTHORIZED, error);
        } catch (Exception e) {
            log.error("Error while converting token " + DDP_TOKEN + " for request", e);

            response.header("WWW-Authenticate",
                    "Bearer error=\"invalid_token\", error_description=\"The access token failed to validate or is malformed.\"");
            var error = new ApiError(ErrorCodes.INVALID_TOKEN, "The access token failed to validate or is malformed.");
            throw ResponseUtil.haltError(response, HttpStatus.SC_UNAUTHORIZED, error);
        }
    }
}
