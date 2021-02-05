package org.broadinstitute.ddp.service;

import static org.slf4j.LoggerFactory.getLogger;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.broadinstitute.ddp.db.dao.SendGridEventDao;
import org.broadinstitute.ddp.json.sendgrid.SendGridEvent;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;

public class SendGridEventService {

    private static final Logger LOG = getLogger(SendGridEventService.class);

    private final Gson gson;

    public SendGridEventService() {
        gson = createGson();
    }

    public SendGridEvent[] parseSendGridEvents(String sendGridEventsJson) {
        return gson.fromJson(sendGridEventsJson, SendGridEvent[].class);
    }

    public void logSendGridEvents(SendGridEvent[] sendGridEvents, String sendGridEventsJson) {
        LOG.info("SendGrid-EVENT-PAYLOAD: " + sendGridEventsJson);  // temporary for events investigation purposes (TODO: to be removed)
        for (var event : sendGridEvents) {
            if (LOG.isDebugEnabled() || LOG.isTraceEnabled()) {
                LOG.debug("SendGrid-EVENT[{}]: email={}, timestamp={}, sg_event_id={}, reason={}, status={},\n"
                        + "\turl={}, ip={}, sg_message_id={}, response={}, attempt={}, smtp_id={}",
                        event.getEventType(),
                        event.getEmail(),
                        event.getTimestamp(),
                        event.getSgEventId(),
                        event.getReason(),
                        event.getStatus(),
                        event.getUrl(),
                        event.getIp(),
                        event.getSgMessageId(),
                        event.getResponse(),
                        event.getAttempt(),
                        event.getSmtpId());
            } else {
                LOG.info("SendGrid-EVENT[{}]: email={}, timestamp={}, sg_event_id={}, reason={}, status={}",
                        event.getEventType(),
                        event.getEmail(),
                        event.getTimestamp(),
                        event.getSgEventId(),
                        event.getReason(),
                        event.getStatus());
            }
        }
    }

    public void persistLogEvents(Handle handle, SendGridEvent[] sendGridEvents) {
        var sendGridEventDao = handle.attach(SendGridEventDao.class);
        for (var sendGridEvent : sendGridEvents) {
            sendGridEventDao.insertSendGridEvent(sendGridEvent);
        }
    }

    private Gson createGson() {
        return new GsonBuilder().create();
    }
}
