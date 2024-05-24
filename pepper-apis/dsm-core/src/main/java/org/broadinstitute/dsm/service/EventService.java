package org.broadinstitute.dsm.service;

import java.util.Collection;
import java.util.concurrent.Callable;

import com.google.common.annotations.VisibleForTesting;
import com.sun.istack.NotNull;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import lombok.NonNull;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.SkippedParticipantEventDao;
import org.broadinstitute.dsm.db.dao.queue.EventDao;
import org.broadinstitute.dsm.model.KitDDPNotification;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.util.DDPRequestUtil;
import org.broadinstitute.dsm.util.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventService {
    private static final Logger logger = LoggerFactory.getLogger(EventService.class);
    protected static final int MAX_TRIES = 5;
    private static final EventDao eventDao = new EventDao();
    private static final SkippedParticipantEventDao skippedParticipantEventDao = new SkippedParticipantEventDao();

    /**
     * <p>
     * Called from Kit Classes, this method adds the kit event to the EVENT_QUEUE table and triggers the Dss for the event.
     * If the event is skipped for this participant, the event will not be triggered, just added into the EVENT_QUEUE table.
     * </p><p>
     * The event that is sent to DSS has the ddpKitRequestId as the eventInfo, however, the event that is added to the
     * EVENT_QUEUE table will have the dsmKitRequestId as the eventInfo.
     * </p>
     * @param kitDDPNotification - the kit event to be triggered
     * */
    public static void sendKitEventToDss(@NonNull KitDDPNotification kitDDPNotification) {
        boolean dssSuccessfullyTriggered = false;
        if (isParticipantEventSkipped(kitDDPNotification)) {
            logger.info("Participant event was skipped per data in participant_event table. DDP will not get triggered");
        } else  {
            dssSuccessfullyTriggered = triggerDssByKitEvent(kitDDPNotification);
        }
        addKitEvent(kitDDPNotification.getEventName(), kitDDPNotification.getDdpInstanceId(),
                kitDDPNotification.getDsmKitRequestId(), dssSuccessfullyTriggered);
    }

    /**
     * Returns true if this event is skipped for this participant, false otherwise
     * */
    private static boolean isParticipantEventSkipped(KitDDPNotification kitDDPNotification) {
        return skippedParticipantEventDao.isParticipantEventSkipped(kitDDPNotification.getParticipantId(),
                kitDDPNotification.getEventName(), kitDDPNotification.getDdpInstanceId());
    }

    /**
     * Called in ClinicalKitDao and BasePatch classes, this method adds the participant event to the EVENT_QUEUE table
     * and triggers the Dss for the event.
     * The Only participant_event currently in DSM is "REQUIRED_SAMPLES_RECEIVED" for PE-CGS studies
     * */
    public static void sendParticipantEventToDss(String eventName, DDPInstance ddpInstance, @NotNull String ddpParticipantId) {
        int ddpInstanceId = ddpInstance.getDdpInstanceIdAsInt();
        if (!skippedParticipantEventDao.isParticipantEventSkipped(ddpParticipantId, eventName, ddpInstanceId)) {
            boolean dssSuccessfullyTriggered = triggerDssWithEvent(eventName, ddpInstance, System.currentTimeMillis() / 1000,
                    ddpParticipantId, ddpParticipantId, null);
            addParticipantEvent(eventName, ddpInstanceId, ddpParticipantId, dssSuccessfullyTriggered);
        } else {
            logger.info("Participant event was skipped for event %s for participant %s ".formatted(eventName, ddpParticipantId)
                            .concat("based on participant_event table. Dss will not get triggered"));
            //add this event to the EVENT_QUEUE table, but without triggering dss and flag EVENT_TRIGGERED = false
            addParticipantEvent(eventName, ddpInstanceId, ddpParticipantId, false);
        }
    }

    /**
     * Sends a POST request to DSS to trigger the event for a kit, using the information in the KitDDPNotification object.
     * */
    @VisibleForTesting
    protected static boolean triggerDssByKitEvent(KitDDPNotification kitDDPNotification) {
        DDPInstance ddpInstance = DDPInstance.getDDPInstanceById(kitDDPNotification.getDdpInstanceId());
        return triggerDssWithEvent(kitDDPNotification.getEventName(), ddpInstance,
                kitDDPNotification.getDate() / 1000, kitDDPNotification.getParticipantId(),
                kitDDPNotification.getDdpKitRequestId(), kitDDPNotification.getUploadReason());
    }

    /**
     * Tries to send a POST request to DSS to trigger the event for a kit or a participant,
     * It will try MAX_TRIES times before giving up and adding the event to the EVENT_QUEUE table.
     * @param eventType - the type of event to be triggered, e.g. "SALIVA_RECEIVED"
     * @param eventInfo - the information to be sent in the event, for kit events it is the "ddp_kit_request_id",
     *      and for participant events it is the ddp_participant_id
     * @return true if the request was successful, false otherwise
     * */
    @VisibleForTesting
    protected static boolean triggerDssWithEvent(@NonNull String eventType, DDPInstance ddpInstance, long eventDate,
                                        @NotNull String ddpParticipantId, @NotNull String eventInfo, String reason) {
        final long initialInterval = 100; // base delay in milliseconds
        final double multiplier = 2.0; // exponential backoff multiplier

        IntervalFunction intervalFn = IntervalFunction.ofExponentialBackoff(initialInterval, multiplier);
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(MAX_TRIES)
                .intervalFunction(intervalFn)
                .build();
        Retry retry = Retry.of("triggerDssWithEvent", retryConfig);

        Callable<Boolean> triggerDssWithEventCallable = Retry.decorateCallable(retry, () -> {
            boolean success = false;
            try {
                success = sendDDPEventRequest(eventType, ddpInstance, eventDate, ddpParticipantId, eventInfo, reason);
            } catch (Exception e) {
                logTriggerFailure(ddpInstance, eventType, ddpParticipantId, eventInfo, e);
            }
            if (success) {
                return true;
            } else {
                throw new Exception("Failed to trigger event");
            }
        });

        try {
            return triggerDssWithEventCallable.call();
        } catch (Exception e) {
            logTriggerExhausted(ddpInstance, eventType, ddpParticipantId, eventInfo);
            return false;
        }
    }

    /**
     * Sends the POST request to DSS to trigger the event for a kit or a participant.
     * */
    @VisibleForTesting
    protected static boolean sendDDPEventRequest(String eventType, DDPInstance ddpInstance, long eventDate, String ddpParticipantId,
                                        String eventInfo, String reason) throws Exception {
        Event event = new Event(ddpParticipantId, eventType, eventDate, reason, eventInfo);
        String sendRequest = ddpInstance.getBaseUrl() + RoutePath.DDP_PARTICIPANT_EVENT_PATH + "/" + ddpParticipantId;
        int responseCode = DDPRequestUtil.postRequest(sendRequest, event, ddpInstance.getName(), ddpInstance.isHasAuth0Token());
        if (responseCode == 200) {
            return true;
        }
        logger.error("Bad Response: Failed to trigger {} to notify participant {} about {} for dsm_kit_request_id {}, response code {}",
                ddpInstance.getName(), ddpParticipantId, eventType, eventInfo, responseCode);
        return false;
    }

    /**
     * logs the failure of the trigger event to DSS
     * */
    @VisibleForTesting
    protected static void logTriggerFailure(DDPInstance ddpInstance, String eventType, String ddpParticipantId, String eventInfo,
                                   Exception e) {
        logger.warn("Failed to trigger {} to notify participant {} about {} for dsm_kit_request_id {}",
                ddpInstance.getName(), ddpParticipantId, eventType, eventInfo);
        logger.error("Error: ", e);
    }

    /**
     * logs the exhaustion of the tries to trigger the event to DSS
     * */
    @VisibleForTesting
    protected static void logTriggerExhausted(DDPInstance ddpInstance, String eventType, String ddpParticipantId, String eventInfo) {
        logger.error("DSM was unable to send the trigger to DSS for study {}, participant {}, event type {} and event info {},"
                        + " exhausted all tries. Event will be added to the EVENT_QUEUE table with trigger false", ddpInstance.getName(),
                ddpParticipantId, eventType, eventInfo);
    }

    /**
     * Adds a kit event to the EVENT_QUEUE table
     * */
    private static void addKitEvent(@NonNull String name, @NonNull int ddpInstanceID, @NonNull String requestId, boolean trigger) {
        eventDao.insertEvent(name, ddpInstanceID, null, requestId, trigger);

    }

    /**
     * Adds a Participant event to the EVENT_QUEUE table
     * */
    private static void addParticipantEvent(@NonNull String name, @NonNull int instanceID, @NonNull String ddpParticipantId,
                                           boolean trigger) {
        eventDao.insertEvent(name, instanceID, ddpParticipantId, null, trigger);
    }

    /**
     * Triggers the reminder emails for kits that were sent out but have not been received yet.
     * The events tell DSS the list of kits that need reminders, and then DSS will send the reminder emails.
     * It's called from the DDPEventJob class
     * */
    public static void triggerReminder() {
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
