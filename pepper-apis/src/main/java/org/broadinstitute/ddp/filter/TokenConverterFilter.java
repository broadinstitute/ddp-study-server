package org.broadinstitute.ddp.filter;

import static org.broadinstitute.ddp.constants.RouteConstants.Header.AUTHORIZATION;
import static spark.Spark.halt;

import org.broadinstitute.ddp.security.DDPAuth;
import org.broadinstitute.ddp.security.JWTConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Filter;
import spark.Request;
import spark.Response;

/**
 * Verifies the integrity of the JWT and adds
 * a {@link org.broadinstitute.ddp.security.DDPAuth ddp auth object} as a request
 * {@link #DDP_TOKEN attribute} so that downstream processing
 * can read token values without having to re-verify.
 */
public class TokenConverterFilter implements Filter {

    public static final String DDP_TOKEN = "DDP_AUTH";
    private static final Logger LOG = LoggerFactory.getLogger(TokenConverterFilter.class);
    private final JWTConverter jwtConverter;

    public TokenConverterFilter(JWTConverter jwtConverter) {
        this.jwtConverter = jwtConverter;
    }

    @Override
    public void handle(Request request, Response response) {
        try {
            DDPAuth ddpAuth = jwtConverter.convertJWTFromHeader(request.headers(AUTHORIZATION));
            request.attribute(DDP_TOKEN, ddpAuth);
        } catch (Exception e) {
            LOG.error("Error while converting token " + DDP_TOKEN + " for request", e);
            halt(401);
        }
    }
}
