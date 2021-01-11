package org.broadinstitute.ddp.route;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

/**
 * Handler for POST ../auth0-log-event
 * 
 * <p>TODO: Current version of the class is just for investigating of log event data passed from Auth0
 * (this POST endpoint should be registered in a Auth0 custom webhook stream.
 * Next version will save the log event to an appropriate DB table(s).
 */
public class Auth0LogEventRoute implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(Auth0LogEventRoute.class);

    @Override
    public Object handle(Request request, Response response) throws Exception {
        LOG.info("AUTH0-LOG-EVENT:\n-------------------------\n" + request.body() + "\n-------------------------");
        return null;
    }

}
