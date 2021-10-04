package org.broadinstitute.ddp.route;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_OK;
import static org.broadinstitute.ddp.constants.ErrorCodes.DATA_PERSIST_ERROR;
import static org.broadinstitute.ddp.constants.ErrorCodes.MISSING_BODY;
import static org.broadinstitute.ddp.constants.ErrorCodes.REQUIRED_PARAMETER_MISSING;
import static org.broadinstitute.ddp.json.auth0.Auth0LogEvent.createInstance;
import static org.slf4j.LoggerFactory.getLogger;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.json.auth0.Auth0LogEvent;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.service.Auth0LogEventService;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.jdbi.v3.core.JdbiException;
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

    private final Auth0LogEventService auth0LogEventService;

    public Auth0LogEventRoute(final Auth0LogEventService auth0LogEventService) {
        this.auth0LogEventService = auth0LogEventService;
    }

    @Override
    public Object handle(Request request, Response response) throws Exception {
        String tenant = readTenant(request);
        checkBody(request);
        var logEvents = auth0LogEventService.parseAuth0LogEvents(request.body());
        for (var logEvent : logEvents) {
            persistLogEvent(auth0LogEventService, createInstance(logEvent, tenant));
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
            TransactionWrapper.useTxn(handle -> {
                if (auth0LogEventService.persistAuth0LogEvent(handle, logEventObject)) {
                    auth0LogEventService.logAuth0LogEvent(logEventObject);
                }
            });
        } catch (Exception e) {
            haltError(SC_INTERNAL_SERVER_ERROR, DATA_PERSIST_ERROR, preProcessExceptionMessage(e));
        }
    }

    private String preProcessExceptionMessage(Exception e) {
        final String ERROR_TEXT__CODE_CANNOT_BE_NULL = "Column 'auth0_log_event_code_id' cannot be null";
        if (e instanceof JdbiException) {
            if (e.getMessage().contains(ERROR_TEXT__CODE_CANNOT_BE_NULL)) {
                return "Error: trying to insert null-value to column 'auth0_log_event_code_id' in table `auth0_log_event` . "
                        + "It's null because a log event code is not found in table `auth0_log_event_code` - the event contains "
                        + "an unknown code. Please, check URL `https://auth0.com/docs/monitor-auth0/logs/log-event-type-codes` "
                        + "for new codes and create Liquibase script with insert statements to insert new codes to table "
                        + "`auth0_log_event_code`."
                        + "\nCause error:\n"
                        + e.getMessage();
            }
        }
        return e.getMessage();
    }

    private void haltError(int status, String code, String msg) {
        LOG.error(msg);
        throw ResponseUtil.haltError(status, new ApiError(code, msg));
    }
}
