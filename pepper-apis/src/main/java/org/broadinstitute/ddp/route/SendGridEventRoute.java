package org.broadinstitute.ddp.route;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_OK;
import static org.broadinstitute.ddp.constants.ErrorCodes.DATA_PERSIST_ERROR;
import static org.broadinstitute.ddp.constants.ErrorCodes.MISSING_BODY;
import static org.slf4j.LoggerFactory.getLogger;


import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.json.sendgrid.SendGridEvent;
import org.broadinstitute.ddp.service.SendGridEventService;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.slf4j.Logger;
import spark.Request;
import spark.Response;
import spark.Route;

public class SendGridEventRoute implements Route {

    private static final Logger LOG = getLogger(SendGridEventRoute.class);

    private final SendGridEventService sendGridEventService;

    public SendGridEventRoute() {
        this.sendGridEventService = new SendGridEventService();
    }

    @Override
    public Object handle(Request request, Response response) throws Exception {
        checkBody(request);
        var sendGridEvents = sendGridEventService.parseSendGridEvents(request.body());
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
            haltError(SC_INTERNAL_SERVER_ERROR, DATA_PERSIST_ERROR, e.getMessage());
        }
    }

    private void checkBody(Request request) {
        if (StringUtils.isBlank(request.body())) {
            haltError(SC_BAD_REQUEST, MISSING_BODY, "Body not specified");
        }
    }

    private void haltError(int status, String code, String msg) {
        LOG.warn(msg);
        throw ResponseUtil.haltError(status, new ApiError(code, msg));
    }
}
