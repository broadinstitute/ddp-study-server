package org.broadinstitute.ddp.route;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static org.broadinstitute.ddp.constants.ConfigFile.AUTH0_LOG_EVENT_API_AUTHORIZATION_TOKEN;
import static org.broadinstitute.ddp.constants.ErrorCodes.INVALID_TOKEN;
import static org.broadinstitute.ddp.constants.ErrorCodes.MALFORMED_HEADER;
import static org.broadinstitute.ddp.constants.ErrorCodes.REQUIRED_PARAMETER_MISSING;
import static org.broadinstitute.ddp.constants.RouteConstants.Header.AUTHORIZATION;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.List;
import java.util.Map;


import com.google.gson.JsonElement;
import com.typesafe.config.Config;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.service.Auth0LogEventService;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.slf4j.Logger;
import spark.Request;
import spark.Response;
import spark.Route;

/**
 * Handler for POST ../auth0-log-event?tenant=TENANT_NAME
 *
 * <p>JSON (sent in payload) is parsed and auth0 log events are extracted from it.
 * Then log events are logged and persisted into table 'auth0_log_event'.
 *
 * <p>Header ("Authorization") to be specified as a parameter in Auth0 Custom Webhook definition.
 * It can contain API token.<br>
 * This is OPTIONAL:<br>
 * - if config parameter 'auth0.auth0LogEventApiAuthorizationToken' is specified (and it should contain
 *   the token value), then on the other side (in Auth0 Custom Webhook) it should be defined,
 *   and it will be checked by {@link Auth0LogEventRoute};<br>
 * - if config parameter 'auth0.auth0LogEventApiAuthorizationToken' is not specified or empty then
 *   token is NOT checked by {@link Auth0LogEventRoute}.
 */
public class Auth0LogEventRoute implements Route {

    private static final Logger LOG = getLogger(Auth0LogEventRoute.class);

    /** Mandatory parameter in the log event URL. Specifies name of auth0 tenant */
    static final String QUERY_PARAM_TENANT = "tenant";

    private final String auth0LogEventApiAuthorizationToken;

    public Auth0LogEventRoute(final Config config) {
        Config auth0Config = config.getConfig(ConfigFile.AUTH0);
        auth0LogEventApiAuthorizationToken = auth0Config.hasPath(AUTH0_LOG_EVENT_API_AUTHORIZATION_TOKEN) ?
            auth0Config.getString(AUTH0_LOG_EVENT_API_AUTHORIZATION_TOKEN) : null;
    }

    @Override
    public Object handle(Request request, Response response) throws Exception {
        String tenant = readTenant(request);
        checkAuthorizationToken(request);

        final Auth0LogEventService auth0LogEventService = new Auth0LogEventService();

        final List<Map<String, JsonElement>> logEvents = auth0LogEventService.parseAuth0LogEvents(request.body());
        for (var logEvent : logEvents) {
            auth0LogEventService.logAuth0LogEvent(logEvent, tenant);
            auth0LogEventService.persistAuth0LogEvent(logEvent, tenant);
        }

        response.status(HttpStatus.SC_OK);
        return "";
    }

    private String readTenant(final Request request) {
        String tenant = request.queryParams(QUERY_PARAM_TENANT);
        if (tenant==null) {
            haltError(SC_BAD_REQUEST, REQUIRED_PARAMETER_MISSING, "Parameter not specified: " + QUERY_PARAM_TENANT);
        }
        return tenant;
    }

    private void checkAuthorizationToken(final Request request) {
        if (isNotBlank(auth0LogEventApiAuthorizationToken)) {
            String authorizationToken = request.headers(AUTHORIZATION);
            if (authorizationToken == null) {
                haltError(SC_BAD_REQUEST, MALFORMED_HEADER, "Header not specified: " + AUTHORIZATION);
            }
            if (!auth0LogEventApiAuthorizationToken.equals(authorizationToken)) {
                haltError(SC_UNAUTHORIZED, INVALID_TOKEN, "Invalid authorization token");
            }
        }
    }

    private void haltError(int status, String code, String msg) {
        LOG.warn(msg);
        throw ResponseUtil.haltError(status, new ApiError(code, msg));
    }
}
