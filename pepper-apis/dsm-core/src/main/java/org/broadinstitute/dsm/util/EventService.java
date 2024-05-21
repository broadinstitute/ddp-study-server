package org.broadinstitute.dsm.util;

import java.io.IOException;
import java.util.Collection;

import com.sun.istack.NotNull;
import lombok.NonNull;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.SkippedParticipantEvent;
import org.broadinstitute.dsm.db.dao.queue.EventDao;
import org.broadinstitute.dsm.model.KitDDPNotification;
import org.broadinstitute.dsm.statics.RoutePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventService {
    private static final Logger logger = LoggerFactory.getLogger(EventService.class);
    private static final int MAX_TRIES = 3;

    private EventDao eventDao = new EventDao();

    //used in kit functionality classes
    /**
     * Called from Kit Classes, this method adds the kit event to the EVENT_QUEUE table and triggers the Dss for the event.
     * If the event is skipped for this participant, the event will not be triggered, just added into the EVENT_QUEUE table.
     * @param kitDDPNotification - the kit event to be triggered
     * */
    public void sendKitEventToDss(@NonNull KitDDPNotification kitDDPNotification) {
        boolean dssSuccessfullyTriggered = false;
        if (isParticipantEventSkipped(kitDDPNotification)) {
            logger.info("Participant event was skipped per data in participant_event table. DDP will not get triggered");
        } else  {
            dssSuccessfullyTriggered = triggerDDPForKitEvent(kitDDPNotification);
        }
        addKitEvent(kitDDPNotification.getEventName(), kitDDPNotification.getDdpInstanceId(),
                kitDDPNotification.getDsmKitRequestId(), dssSuccessfullyTriggered);
    }

    /**
     * Returns true if this event is skipped for this participant, false otherwise
     * */
    private static boolean isParticipantEventSkipped(KitDDPNotification kitDDPNotification) {
        return SkippedParticipantEvent.isParticipantEventSkipped(kitDDPNotification.getParticipantId(),
                kitDDPNotification.getEventName(), kitDDPNotification.getDdpInstanceId());
    }

    /**
     * Called in ClinicalKitDao and BasePatch classes, this method adds the participant event to the EVENT_QUEUE table
     * and triggers the Dss for the event.
     * */
    public void sendParticipantEventToDss(String eventName, DDPInstance ddpInstance, @NotNull String ddpParticipantId) {
        int ddpInstanceId = ddpInstance.getDdpInstanceIdAsInt();
        if (!SkippedParticipantEvent.isParticipantEventSkipped(ddpParticipantId, eventName, ddpInstanceId)) {
            triggerDDPWithEvent(eventName, ddpInstance, System.currentTimeMillis() / 1000, ddpParticipantId, ddpParticipantId, null);
            addParticipantEvent(eventName, ddpInstanceId, ddpParticipantId, true);
        } else {
            logger.info("Participant event was skipped for event %s for participant %s ".formatted(eventName, ddpParticipantId)
                    + "based on participant_event table. Dss will not get triggered");
            //add this event to the EVENT_QUEUE table, but without triggering the ddp and flag EVENT_TRIGGERED = false
            addParticipantEvent(eventName, ddpInstanceId, ddpParticipantId, false);
        }
    }

    private boolean triggerDDPForKitEvent(KitDDPNotification kitDDPNotification) {
        DDPInstance ddpInstance = DDPInstance.getDDPInstanceById(kitDDPNotification.getDdpInstanceId());
        return triggerDDPWithEvent(kitDDPNotification.getEventName(), ddpInstance,
                kitDDPNotification.getDate() / 1000, kitDDPNotification.getParticipantId(),
                kitDDPNotification.getDdpKitRequestId(), kitDDPNotification.getUploadReason());
    }

    private boolean triggerDDPWithEvent(@NonNull String eventType, DDPInstance ddpInstance, long eventDate,
                                        @NotNull String ddpParticipantId, @NotNull String eventInfo, String reason) {
        for (int tries = 0; tries < MAX_TRIES; tries++) {
            try {
                if (sendDDPEventRequest(eventType, ddpInstance, eventDate, ddpParticipantId, eventInfo, reason)) {
                    return true;
                }
            } catch (IOException e) {
                logTriggerFailure(ddpInstance, eventType, ddpParticipantId, eventInfo, tries, e);
            }
        }
        logTriggerExhausted(ddpInstance, eventType, ddpParticipantId, eventInfo);
        return false;
    }

    private boolean sendDDPEventRequest(String eventType, DDPInstance ddpInstance, long eventDate, String ddpParticipantId,
                                        String eventInfo, String reason) throws IOException {
        Event event = new Event(ddpParticipantId, eventType, eventDate, reason, eventInfo);
        String sendRequest = ddpInstance.getBaseUrl() + RoutePath.DDP_PARTICIPANT_EVENT_PATH + "/" + ddpParticipantId;
        int response = DDPRequestUtil.postRequest(sendRequest, event, ddpInstance.getName(), ddpInstance.isHasAuth0Token());
        if (response == 200) {
            return true;
        }
        logger.warn("POST request to {} failed with response code {}, for participant {} about {} for dsm_kit_request_id {} in try {}",
                ddpInstance.getName(), response, ddpParticipantId, eventType, eventInfo, 0);
        return false;
    }

    private void logTriggerFailure(DDPInstance ddpInstance, String eventType, String ddpParticipantId, String eventInfo, int tries,
                                   IOException e) {
        logger.warn("Failed to trigger {} to notify participant {} about {} for dsm_kit_request_id {} in try {}",
                ddpInstance.getName(), ddpParticipantId, eventType, eventInfo, tries);
        logger.error("IOException:", e);
    }

    private void logTriggerExhausted(DDPInstance ddpInstance, String eventType, String ddpParticipantId, String eventInfo) {
        logger.error("DSM was unable to send the trigger to DSS for study {}, participant {}, event type {} and event info {},"
                        + " exhausted all tries. Event will be added to the EVENT_QUEUE table", ddpInstance.getName(), ddpParticipantId,
                eventType, eventInfo);
    }

    public void addKitEvent(@NonNull String name, @NonNull int ddpInstanceID, @NonNull String requestId, boolean trigger) {
        eventDao.insertEvent(name, ddpInstanceID, null, requestId, trigger);

    }

    public void addParticipantEvent(@NonNull String name, @NonNull int instanceID, @NonNull String ddpParticipantId,
                                           boolean trigger) {
        eventDao.insertEvent(name, instanceID, ddpParticipantId, null, trigger);
    }

    public void triggerReminder() {
        logger.info("Triggering reminder emails now");
        Collection<KitDDPNotification> kitDDPNotifications = eventDao.getKitsNotReceived();
        for (KitDDPNotification kitInfo : kitDDPNotifications) {
            if (KitDDPNotification.REMINDER.equals(kitInfo.getEventType())) {
                sendKitEventToDss(kitInfo);
            } else {
                logger.error("Event type {} is not a reminder event", kitInfo.getEventType());
            }
        }
    }
}
