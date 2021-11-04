package org.broadinstitute.dsm.route;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.handlers.util.Result;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.ParticipantEvent;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.UserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

public class ParticipantEventRoute extends RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(ParticipantEventRoute.class);

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        String requestBody = request.body();
        String realm = request.params(RequestParameter.REALM);
        if (StringUtils.isNotBlank(realm)) {
            if (UserUtil.checkUserAccess(realm, userId, "participant_event", null)) {
                return ParticipantEvent.getSkippedParticipantEvents(realm);
            }
            else {
                response.status(500);
                return new Result(500, UserErrorMessages.NO_RIGHTS);
            }
        }

        if (requestBody != null) {
            long currentTime = System.currentTimeMillis();
            JsonObject jsonObject = new JsonParser().parse(request.body()).getAsJsonObject();
            realm = jsonObject.getAsJsonObject().get("realm").getAsString();
            if (UserUtil.checkUserAccess(realm, userId, "participant_event", null)) {
                DDPInstance instance = DDPInstance.getDDPInstance(realm);

                String ddpParticipantId = jsonObject.getAsJsonObject().get("participantId").getAsString();
                String userIdRequest = jsonObject.getAsJsonObject().get("user").getAsString();
                String eventType = jsonObject.getAsJsonObject().get("eventType").getAsString();

                ParticipantEvent.skipParticipantEvent(ddpParticipantId, currentTime, userIdRequest, instance, eventType);
                return new Result(200);
            }
            else {
                response.status(500);
                return new Result(500, UserErrorMessages.NO_RIGHTS);
            }
        }
        logger.error("Request body was missing");
        return new Result(500);
    }
}
