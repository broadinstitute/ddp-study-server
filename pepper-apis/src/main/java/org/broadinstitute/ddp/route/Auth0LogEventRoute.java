package org.broadinstitute.ddp.route;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_OK;
import static org.broadinstitute.ddp.constants.ErrorCodes.DATA_PERSIST_ERROR;
import static org.broadinstitute.ddp.constants.ErrorCodes.MISSING_BODY;
import static org.broadinstitute.ddp.constants.ErrorCodes.REQUIRED_PARAMETER_MISSING;
import static org.slf4j.LoggerFactory.getLogger;


import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.json.auth0.Auth0LogEvent;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.service.Auth0LogEventService;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.slf4j.Logger;
import spark.Request;
import spark.Response;
import spark.Route;

/**
 * Handler for POST ../auth0-log-event?tenant=[TENANT_NAME]
 *
 * <p>JSON (sent in payload) is parsed and auth0 log events are extracted from it.
 * Then log events are logged and persisted into table 'auth0_log_event'.
 */
public class Auth0LogEventRoute implements Route {

    private static final Logger LOG = getLogger(Auth0LogEventRoute.class);

    /** Mandatory parameter in the log event URL. Specifies name of auth0 tenant */
    public static final String QUERY_PARAM_TENANT = "tenant";

    @Override
    public Object handle(Request request, Response response) throws Exception {
        String tenant = readTenant(request);
        checkBody(request);

        var auth0LogEventService = new Auth0LogEventService();

        var logEvents = auth0LogEventService.parseAuth0LogEvents(request.body());
        for (var logEvent : logEvents) {
            var logEventObject = Auth0LogEvent.createInstance(logEvent, tenant);
            persistLogEvent(auth0LogEventService, logEventObject);
            auth0LogEventService.logAuth0LogEvent(logEventObject);
        }

        response.status(SC_OK);
        return "";
    }

    private String readTenant(Request request) {
        String tenant = request.queryParams(QUERY_PARAM_TENANT);
        if (tenant == null) {
            haltError(SC_BAD_REQUEST, REQUIRED_PARAMETER_MISSING, "Parameter not specified: " + QUERY_PARAM_TENANT);
        }
        return tenant;
    }

    private void checkBody(Request request) {
        if (StringUtils.isBlank(request.body())) {
            haltError(SC_BAD_REQUEST, MISSING_BODY, "Body not specified");
        }
    }

    private void persistLogEvent(Auth0LogEventService auth0LogEventService, Auth0LogEvent logEventObject) {
        try {
            TransactionWrapper.useTxn(handle -> auth0LogEventService.persistAuth0LogEvent(handle, logEventObject));
        } catch (Exception e) {
            haltError(SC_INTERNAL_SERVER_ERROR, DATA_PERSIST_ERROR, e.getMessage());
        }
    }

    private void haltError(int status, String code, String msg) {
        LOG.warn(msg);
        throw ResponseUtil.haltError(status, new ApiError(code, msg));
    }
}
