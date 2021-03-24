package org.broadinstitute.dsm.route;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.broadinstitute.ddp.handlers.util.Result;
import org.broadinstitute.dsm.db.EditParticipantMessage;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.util.List;
import java.util.Map;

public class EditParticipantMessageReceiverRoute extends RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(EditParticipantPublisherRoute.class);

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        if (RoutePath.RequestMethod.GET.toString().equals(request.requestMethod())) {
            EditParticipantMessage messageWithStatus = EditParticipantMessage.getMessageWithStatus(Integer.parseInt(userId));
            int messageId = messageWithStatus.getMessageId();
            String status = messageWithStatus.getMessageStatus();
            String message = messageWithStatus.getReceived_message();

            logger.info("Sending back answer according to message status");

            if (DBConstants.MESSAGE_RECEIVED_STATUS.equals(status)) {
                EditParticipantMessage.updateMessageStatusById(messageId, DBConstants.MESSAGE_SENT_BACK_STATUS);
                return new Result(200, new JSONObject(message).toString());
            }
            return new Result(200, new JSONObject("{}").toString());
        }
        logger.error("Request method not known");
        response.status(500);
        return new Result(500, UserErrorMessages.CONTACT_DEVELOPER);
    }
}
