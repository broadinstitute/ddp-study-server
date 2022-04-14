package org.broadinstitute.ddp.route;

import org.broadinstitute.ddp.constants.ErrorCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

/**
 * Used for testing HTTP status error codes
 * Should throw an exception when accessed
 * e.g. via localhost:5555/error
 */
public class ErrorRoute implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(ErrorRoute.class);

    public ErrorRoute() {
    }

    @Override
    public Object handle(Request request, Response response) throws Exception {
        LOG.info("Currently at: {}", request.url());
        //Only used for testing error exception handling
        throw new Exception(ErrorCodes.SERVER_ERROR);
    }
}
