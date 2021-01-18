package org.broadinstitute.ddp.route;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static org.broadinstitute.ddp.constants.ConfigFile.PrivateApisTokens.AUTH0_LOG_EVENT_BEARER_TOKEN;
import static org.broadinstitute.ddp.constants.ErrorCodes.DATA_PERSIST_ERROR;
import static org.broadinstitute.ddp.constants.ErrorCodes.INVALID_TOKEN;
import static org.broadinstitute.ddp.constants.ErrorCodes.MISSING_BODY;
import static org.broadinstitute.ddp.constants.ErrorCodes.REQUIRED_HEADER_MISSING;
import static org.broadinstitute.ddp.constants.ErrorCodes.REQUIRED_PARAMETER_MISSING;
import static org.broadinstitute.ddp.constants.RouteConstants.Header.AUTHORIZATION;
import static org.broadinstitute.ddp.constants.RouteConstants.Header.BEARER;
import static org.slf4j.LoggerFactory.getLogger;


import com.typesafe.config.Config;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.json.auth0.Auth0LogEvent;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.service.Auth0LogEventService;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.SystemUtil;
import org.slf4j.Logger;
import spark.Request;
import spark.Response;
import spark.Route;

/**
 * Handler for POST ../auth0-log-event?tenant=[TENANT_NAME]
 *
 * <p>JSON (sent in payload) is parsed and auth0 log events are extracted from it.
 * Then log events are logged and persisted into table 'auth0_log_event'.
 *
 * <p>Header ("Authorization") with authorization token can be (optionally) specified in
 * Auth0 Custom Webhook definition.<br>
 * If config parameter 'privateApisTokens.auth0LogEventBearerToken' is specified and not empty
 * then it contains a token which should be compared with token in header 'Authorization'
 * (in a format: 'Bearer ' + 'privateApisTokens.auth0LogEventBearerToken').
 * Token check can be disabled with setting system property 'auth0LogEvent.tokenCheck.disable'=true;
 * If token check is enabled and specified in config then the same token should be specified in
 * Auth0 Custom Webhook settings (with prefix 'Bearer ').
 */
public class Auth0LogEventRoute implements Route {

    private static final Logger LOG = getLogger(Auth0LogEventRoute.class);

    /**
     * It is possible to disable auth0 log event URL token check via system property
     * (by default it is enabled and defined by config params).<br>
     * If system property 'auth0LogEvent.tokenCheck.disable' = true then config params are ignored.
     */
    public static final String SYSTEM_PROPERTY_AUTH0_LOG_EVENT_TOKEN_CHECK_DISABLE = "auth0LogEvent.tokenCheck.disable";
    /** By default token check is not disabled */
    public static final boolean DEFAULT_AUTH0_LOG_EVENT_TOKEN_CHECK_DISABLE = false;

    /** Mandatory parameter in the log event URL. Specifies name of auth0 tenant */
    public static final String QUERY_PARAM_TENANT = "tenant";

    private final Config config;

    public Auth0LogEventRoute(final Config config) {
        this.config = config;
    }

    @Override
    public Object handle(Request request, Response response) throws Exception {
        String tenant = readTenant(request);
        checkAuthorizationToken(request);
        checkBody(request);

        final var auth0LogEventService = new Auth0LogEventService();

        final var logEvents = auth0LogEventService.parseAuth0LogEvents(request.body());
        for (final var logEvent : logEvents) {
            final var logEventObject = Auth0LogEvent.createInstance(logEvent, tenant);
            persistLogEvent(auth0LogEventService, logEventObject);
            auth0LogEventService.logAuth0LogEvent(logEventObject);
        }

        response.status(SC_OK);
        return "";
    }

    private String readTenant(final Request request) {
        String tenant = request.queryParams(QUERY_PARAM_TENANT);
        if (tenant == null) {
            haltError(SC_BAD_REQUEST, REQUIRED_PARAMETER_MISSING, "Parameter not specified: " + QUERY_PARAM_TENANT);
        }
        return tenant;
    }

    private void checkAuthorizationToken(final Request request) {
        if (isCheckToken()) {
            String auth0LogEventApiBearerToken = config.getString(AUTH0_LOG_EVENT_BEARER_TOKEN);
            if (isNotBlank(auth0LogEventApiBearerToken)) {
                String authorizationToken = request.headers(AUTHORIZATION);
                if (authorizationToken == null) {
                    haltError(SC_BAD_REQUEST, REQUIRED_HEADER_MISSING, "Header not specified: " + AUTHORIZATION);
                }
                if (!addBearerPrefixToToken(auth0LogEventApiBearerToken).equals(authorizationToken)) {
                    haltError(SC_UNAUTHORIZED, INVALID_TOKEN, "Invalid authorization token");
                }
            }
        }
    }

    private boolean isCheckToken() {
        return !SystemUtil.readSystemProperty(SYSTEM_PROPERTY_AUTH0_LOG_EVENT_TOKEN_CHECK_DISABLE,
                DEFAULT_AUTH0_LOG_EVENT_TOKEN_CHECK_DISABLE)
                && config.hasPath(AUTH0_LOG_EVENT_BEARER_TOKEN);
    }

    private void checkBody(final Request request) {
        if (StringUtils.isBlank(request.body())) {
            haltError(SC_BAD_REQUEST, MISSING_BODY, "Body not specified");
        }
    }

    private void persistLogEvent(final Auth0LogEventService auth0LogEventService, final Auth0LogEvent logEventObject) {
        try {
            TransactionWrapper.useTxn(handle -> auth0LogEventService.persistAuth0LogEvent(handle, logEventObject));
        } catch (Exception e) {
            haltError(SC_INTERNAL_SERVER_ERROR, DATA_PERSIST_ERROR, e.getMessage());
        }
    }

    private String addBearerPrefixToToken(String token) {
        return BEARER + token;
    }

    private void haltError(int status, String code, String msg) {
        LOG.warn(msg);
        throw ResponseUtil.haltError(status, new ApiError(code, msg));
    }
}
