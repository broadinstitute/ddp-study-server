package org.broadinstitute.ddp.route;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_OK;
import static org.broadinstitute.ddp.constants.ErrorCodes.DATA_PERSIST_ERROR;
import static org.broadinstitute.ddp.constants.ErrorCodes.MISSING_BODY;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.json.sendgrid.SendGridEvent;
import org.broadinstitute.ddp.service.SendGridEventService;
import org.broadinstitute.ddp.util.ResponseUtil;
import spark.Request;
import spark.Response;
import spark.Route;

@Slf4j
@AllArgsConstructor
public class SendGridEventRoute implements Route {
    private final SendGridEventService sendGridEventService;

    @Override
    public Object handle(Request request, Response response) throws Exception {
        String requestBody = request.body();
        if (StringUtils.isBlank(requestBody)) {
            log.warn("Body not found in SendGrid Event Request.. retrieving from request attribute");
            requestBody = request.attribute(RouteConstants.QueryParam.SENDGRID_EVENT_REQUEST_BODY);
            if (StringUtils.isBlank(requestBody)) {
                haltError(SC_BAD_REQUEST, MISSING_BODY, "Body not specified", null);
            }
        }

        var sendGridEvents = sendGridEventService.parseSendGridEvents(requestBody);
        if (sendGridEvents.length > 0) {
            sendGridEventService.logSendGridEvents(sendGridEvents);
            persistLogEvent(sendGridEvents);
        }
        response.status(SC_OK);
        return "";
    }

    private void persistLogEvent(SendGridEvent[] sendGridEvents) {
        try {
            TransactionWrapper.useTxn(handle -> sendGridEventService.persistLogEvents(handle, sendGridEvents));
        } catch (Exception e) {
            haltError(SC_INTERNAL_SERVER_ERROR, DATA_PERSIST_ERROR, "Error saving sendgrid event", e);
        }
    }

    private void haltError(int status, String code, String msg, Exception e) {
        if (e != null) {
            log.warn(msg, e);
        } else {
            log.warn(msg);
        }
        throw ResponseUtil.haltError(status, new ApiError(code, msg));
    }
}
