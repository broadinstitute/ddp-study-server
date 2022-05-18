package org.broadinstitute.ddp.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.SendGridEventDao;
import org.broadinstitute.ddp.json.sendgrid.SendGridEvent;
import org.jdbi.v3.core.Handle;

@Slf4j
public class SendGridEventService {
    private final Gson gson;

    public SendGridEventService() {
        gson = createGson();
    }

    public SendGridEvent[] parseSendGridEvents(String sendGridEventsJson) {
        return gson.fromJson(sendGridEventsJson, SendGridEvent[].class);
    }

    public void logSendGridEvents(SendGridEvent[] sendGridEvents) {
        for (var event : sendGridEvents) {
            if (log.isDebugEnabled() || log.isTraceEnabled()) {
                log.debug("SendGrid-EVENT[{}]: email={}, timestamp={}, sg_event_id={}, reason={}, status={},\n"
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
                log.info("SendGrid-EVENT[{}]: timestamp={}, sg_event_id={}, reason={}, status={}",
                        event.getEventType(),
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
