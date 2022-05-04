package org.broadinstitute.ddp.route;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.constants.ErrorCodes;
import spark.Request;
import spark.Response;
import spark.Route;

/**
 * Used for testing HTTP status error codes
 * Should throw an exception when accessed
 * e.g. via localhost:5555/error
 */
@Slf4j
@NoArgsConstructor
public class ErrorRoute implements Route {
    @Override
    public Object handle(Request request, Response response) throws Exception {
        log.info("Currently at: {}", request.url());
        //Only used for testing error exception handling
        throw new Exception(ErrorCodes.SERVER_ERROR);
    }
}
