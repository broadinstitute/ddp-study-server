package org.broadinstitute.ddp.filter;

import static org.broadinstitute.ddp.constants.RouteConstants.Header.AUTHORIZATION;
import static spark.Spark.halt;

import com.auth0.jwt.exceptions.TokenExpiredException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.security.DDPAuth;
import org.broadinstitute.ddp.security.JWTConverter;
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
            DDPAuth ddpAuth = jwtConverter.convertJWTFromHeader(request.headers(AUTHORIZATION));
            request.attribute(DDP_TOKEN, ddpAuth);
        } catch (TokenExpiredException e) {
            log.error("Found expired token for request", e);
            halt(401);
        } catch (Exception e) {
            log.error("Error while converting token " + DDP_TOKEN + " for request", e);
            halt(401);
        }
    }
}
