package org.broadinstitute.ddp.handlers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.typesafe.config.Config;
import lombok.NonNull;
import org.broadinstitute.ddp.datstat.DatStatUtil;
import org.broadinstitute.ddp.datstat.ParticipantFields;
import org.broadinstitute.ddp.email.EmailRecord;
import org.broadinstitute.ddp.email.Recipient;
import org.broadinstitute.ddp.exception.NullValueException;
import org.broadinstitute.ddp.handlers.util.Event;
import org.broadinstitute.ddp.handlers.util.Result;
import org.broadinstitute.ddp.util.EDCClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Response;

import java.util.HashMap;
import java.util.Map;

public class ParticipantEventHandler extends AbstractRequestHandler<Event>
{
    private static final Logger logger = LoggerFactory.getLogger(ParticipantEventHandler.class);
    private static final String LOG_PREFIX = "PROCESS EVENT REQUEST - ";

    private static Map<String, JsonElement> eventNotificationLookup = new HashMap<>();

    public ParticipantEventHandler(EDCClient edc, Config config) {
        super(Event.class, edc, config);

        JsonArray notifications = (JsonArray)(new JsonParser().parse(config.getString("participantEventNotifications")));
        for (JsonElement notificationInfo : notifications)
        {
            eventNotificationLookup.put(notificationInfo.getAsJsonObject().get("id").getAsString(), notificationInfo);
        }
    }

    @Override
    protected Result processRequest(@NonNull Event event, QueryParamsMap queryParams, Map<String, String> pathParams,
                                    String requestMethod, String token, Response response) {
        try {
            if (!pathParams.isEmpty()) {
                String participantId = pathParams.get(":id");
                DatStatUtil datStatUtil = (DatStatUtil)edc;
                Recipient recipient = datStatUtil.getSimpleParticipantInfoByAltPid(pathParams.get(":id"));

                if (recipient == null) {
                    logger.error(LOG_PREFIX + "Unable to find participant using id = " + participantId);
                    return new Result(404);
                }

                //for now all mapped events are just going to trigger emails
                if (eventNotificationLookup.containsKey(event.getEventType())) {
                    JsonElement notificationInfo = eventNotificationLookup.get(event.getEventType());
                    EmailRecord.add(notificationInfo, null, recipient,null, recipient.getEmail());
                }
                else {
                    logger.warn("Event " + event.getEventType() + " ignored for participant " + participantId);
                }
             }
            else
            {
                throw new RuntimeException("Participant id is missing.");
            }
            return new Result(200);
        } catch (Exception e) {
            logger.error(LOG_PREFIX + "Error fetching request.", e);
            return new Result(500);
        }
    }
}
