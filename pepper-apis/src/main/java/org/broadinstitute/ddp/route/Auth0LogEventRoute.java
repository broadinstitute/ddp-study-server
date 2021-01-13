package org.broadinstitute.ddp.route;

import java.util.List;
import java.util.Map;


import com.google.gson.JsonElement;
import org.broadinstitute.ddp.service.Auth0LogEventService;
import spark.Request;
import spark.Response;
import spark.Route;

/**
 * Handler for POST ../auth0-log-event?tenant=TENANT_NAME
 *
 * <p>JSON (sent in payload) is parsed and auth0 log events are extracted from it.
 * Then log events are logged and persisted into table 'auth0_log_event'.
 */
public class Auth0LogEventRoute implements Route {

    private static final String QUERY_PARAM_TENANT = "tenant";

    @Override
    public Object handle(Request request, Response response) throws Exception {

        final Auth0LogEventService auth0LogEventService = new Auth0LogEventService();

        final List<Map<String, JsonElement>> logEvents = auth0LogEventService.parseAuth0LogEvents(request.body());
        String tenant = request.queryParams(QUERY_PARAM_TENANT);
        for (var logEvent : logEvents) {
            auth0LogEventService.logAuth0LogEvent(logEvent, tenant);
            auth0LogEventService.persistAuth0LogEvent(logEvent, tenant);
        }
        return null;
    }
}
