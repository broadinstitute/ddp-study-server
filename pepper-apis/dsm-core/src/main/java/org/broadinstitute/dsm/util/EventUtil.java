package org.broadinstitute.dsm.util;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;

import com.sun.istack.NotNull;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.SkippedParticipantEvent;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.KitDDPNotification;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.lddp.db.SimpleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventUtil {

    public static final String SQL_SELECT_KIT_FOR_REMINDER_EMAILS =
            "SELECT eve.event_name, eve.event_type, request.ddp_participant_id, request.dsm_kit_request_id, realm.instance_name, "
                    + "realm.base_url, "
                    + "realm.ddp_instance_id, realm.auth0_token, realm.notification_recipients, kit.receive_date, kit.scan_date, "
                    + "request.upload_reason, request.ddp_kit_request_id, "
                    + "(SELECT count(role.name) FROM ddp_instance realm2, ddp_instance_role inRol, instance_role role "
                    + "WHERE realm2.ddp_instance_id = inRol.ddp_instance_id AND inRol.instance_role_id = role.instance_role_id "
                    + "AND role.name = ? AND realm2.ddp_instance_id = realm.ddp_instance_id) AS 'has_role' "
                    + "FROM ddp_kit_request request LEFT JOIN ddp_kit kit ON (kit.dsm_kit_request_id = request.dsm_kit_request_id) "
                    + "LEFT JOIN ddp_instance realm ON (request.ddp_instance_id = realm.ddp_instance_id) "
                    + "LEFT JOIN ddp_participant_exit ex ON (ex.ddp_participant_id = request.ddp_participant_id "
                    + "AND ex.ddp_instance_id = request.ddp_instance_id) "
                    + "LEFT JOIN event_type eve ON (eve.ddp_instance_id = request.ddp_instance_id "
                    + "AND eve.kit_type_id = request.kit_type_id "
                    + "AND eve.event_type = 'REMINDER') "
                    + "LEFT JOIN EVENT_QUEUE queue ON (queue.DSM_KIT_REQUEST_ID = request.dsm_kit_request_id "
                    + "AND queue.EVENT_TYPE = eve.event_name) "
                    + "WHERE ex.ddp_participant_exit_id IS NULL AND kit.scan_date IS NOT NULL "
                    + "AND kit.scan_date <= (UNIX_TIMESTAMP(NOW())-(eve.hours*60*60))*1000 "
                    + "AND kit.receive_date IS NULL AND kit.deactivated_date IS NULL AND realm.is_active = 1 AND queue.EVENT_TYPE IS NULL";
    private static final Logger logger = LoggerFactory.getLogger(EventUtil.class);
    private static final String SQL_INSERT_EVENT =
            "INSERT INTO EVENT_QUEUE SET EVENT_DATE_CREATED = ?, EVENT_TYPE = ?, DDP_INSTANCE_ID = ?, "
                    + "DDP_PARTICIPANT_ID = ?, DSM_KIT_REQUEST_ID = ?, EVENT_TRIGGERED = ?";

    private static final int MAX_TRIES = 3;

    //used in kit functionality classes
    public static void sendKitNotification(@NonNull KitDDPNotification kitDDPNotification) {
        boolean dssSuccessfullyTriggered = false;
        if (!SkippedParticipantEvent.isParticipantEventSkipped(kitDDPNotification.getParticipantId(), kitDDPNotification.getEventName(),
                kitDDPNotification.getDdpInstanceId())) {
            dssSuccessfullyTriggered = triggerDDPForKitEvent(kitDDPNotification);
        } else {
            logger.info("Participant event was skipped per data in participant_event table. DDP will not get triggered");
            //we also have to add these events also to the event table, but without triggering the ddp and flag EVENT_TRIGGERED = false
        }
        addKitEvent(kitDDPNotification.getEventName(), kitDDPNotification.getDdpInstanceId(),
                kitDDPNotification.getDsmKitRequestId(), dssSuccessfullyTriggered);
    }

    //used in ClinicalKitDao and BasePatch class
    public static void triggerDDPForParticipantEvent(String eventName, DDPInstance ddpInstance, @NotNull String ddpParticipantId) {
        int ddpInstanceId = ddpInstance.getDdpInstanceIdAsInt();
        if (!SkippedParticipantEvent.isParticipantEventSkipped(ddpParticipantId, eventName, ddpInstanceId)) {
            triggerDDPWithEvent(eventName, ddpInstance, 0, ddpParticipantId, ddpParticipantId, null);
        } else {
            logger.info("Participant event was skipped per data in participant_event table. DDP will not get triggered");
            //to add these events also to the event table, but without triggering the ddp and flag EVENT_TRIGGERED = false
            addParticipantEvent(eventName, ddpInstanceId, ddpParticipantId, false);
        }
        addParticipantEvent(eventName, ddpInstanceId, ddpParticipantId, true);
    }

    private static boolean triggerDDPForKitEvent(KitDDPNotification kitDDPNotification) {
        DDPInstance ddpInstance = DDPInstance.getDDPInstanceById(kitDDPNotification.getDdpInstanceId());
        return triggerDDPWithEvent(kitDDPNotification.getEventName(), ddpInstance,
                kitDDPNotification.getDate() / 1000, kitDDPNotification.getParticipantId(),
                kitDDPNotification.getDdpKitRequestId(), kitDDPNotification.getUploadReason());
    }

    private static boolean triggerDDPWithEvent(@NonNull String eventType, DDPInstance ddpInstance, long eventDate,
                                               @NotNull String ddpParticipantId, @NotNull String eventInfo, String reason) {
        int tries = 0;
        while (tries < MAX_TRIES) {
            try {
                Event event = new Event(ddpParticipantId, eventType, eventDate, reason, eventInfo);
                String sendRequest = ddpInstance.getBaseUrl() + RoutePath.DDP_PARTICIPANT_EVENT_PATH + "/" + ddpParticipantId;
                int response = DDPRequestUtil.postRequest(sendRequest, event, ddpInstance.getName(), ddpInstance.isHasAuth0Token());
                if (response == 200) {
                    return true;
                }
                logger.warn("POST request to %s failed with response code %d, for participant %s about %s for dsm_kit_request_id %s in"
                                    + " try %d", ddpInstance.getName(), response, ddpParticipantId, eventType, eventInfo, tries);

            } catch (IOException e) {
                logger.warn("Failed to trigger %s to notify participant %s about %s for dsm_kit_request_id %s in try %d".formatted(
                        ddpInstance.getName(), ddpParticipantId, eventType, eventInfo, tries));
                e.printStackTrace();
            }
            tries++;
        }
        logger.error("DSM was unable to send the trigger to DSS for study %s,  participant %s, event type %s and event info %s, "
                        + " exhausted all tries. Event will be added to the EVENT_QUEUE table",
                ddpInstance.getName(), ddpParticipantId, eventType, eventInfo);
        return false;
    }

    public static void addKitEvent(@NonNull String name, @NonNull int ddpInstanceID, @NonNull String requestId, boolean trigger) {
        insertEvent(name, ddpInstanceID, null, requestId, trigger);

    }

    public static void addParticipantEvent(@NonNull String name, @NonNull int instanceID, @NonNull String ddpParticipantId,
                                           boolean trigger) {
        insertEvent(name, instanceID, ddpParticipantId, null, trigger);
    }

    public static void insertEvent(@NonNull String type, @NonNull int ddpInstanceID, String ddpParticipantId,
                                   String dsmKitRequestId, boolean trigger) {
        if (StringUtils.isBlank(ddpParticipantId) && StringUtils.isBlank(dsmKitRequestId)) {
            throw new DsmInternalError(("Both ddpParticipantId and dsmKitRequestId cannot be null, unable to insert event for type %s and "
                    + " instance %d").formatted(type, ddpInstanceID));
        }
        SimpleResult result = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_EVENT)) {
                stmt.setLong(1, System.currentTimeMillis());
                stmt.setString(2, type);
                stmt.setInt(3, ddpInstanceID);
                if (StringUtils.isNotBlank(ddpParticipantId)) {
                    stmt.setString(4, ddpParticipantId);
                } else {
                    stmt.setNull(4, java.sql.Types.VARCHAR);
                }
                if (StringUtils.isNotBlank(dsmKitRequestId)) {
                    stmt.setString(5, dsmKitRequestId);
                } else {
                    stmt.setNull(5, java.sql.Types.VARCHAR);
                }
                stmt.setString(5, dsmKitRequestId);
                stmt.setBoolean(6, trigger);
                int r = stmt.executeUpdate();
                if (r != 1) {
                    if (ddpParticipantId != null) {
                        dbVals.resultException = new DsmInternalError("Error could not add event for participant " + ddpParticipantId);
                    } else {
                        dbVals.resultException = new DsmInternalError("Error could not add event for kit request " + dsmKitRequestId);
                    }
                    return dbVals;
                }
            } catch (SQLException e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });
        if (result.resultException != null) {
            if (ddpParticipantId != null) {
                logger.error("Error adding event for participant " + ddpParticipantId, result.resultException);
            } else {
                logger.error("Error adding event for kit request " + dsmKitRequestId, result.resultException);
            }
        }
    }

    public void triggerReminder() {
        logger.info("Triggering reminder emails now");
        Collection<KitDDPNotification> kitDDPNotifications = getKitsNotReceived();
        for (KitDDPNotification kitInfo : kitDDPNotifications) {
            if (KitDDPNotification.REMINDER.equals(kitInfo.getEventType())) {
                sendKitNotification(kitInfo);
            } else {
                logger.error("Event type " + kitInfo.getEventType() + " is not a reminder event");
            }
        }
    }

    public Collection<KitDDPNotification> getKitsNotReceived() {
        ArrayList<KitDDPNotification> kitDDPNotifications = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_KIT_FOR_REMINDER_EMAILS)) {
                stmt.setString(1, DBConstants.KIT_PARTICIPANT_NOTIFICATIONS_ACTIVATED);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        if (rs.getBoolean(DBConstants.HAS_ROLE)) {
                            String participantId = rs.getString(DBConstants.DDP_PARTICIPANT_ID);
                            String realm = rs.getString(DBConstants.INSTANCE_NAME);
                            kitDDPNotifications.add(new KitDDPNotification(participantId, rs.getString(DBConstants.DSM_KIT_REQUEST_ID),
                                    rs.getInt(DBConstants.DDP_INSTANCE_ID), realm, rs.getString(DBConstants.BASE_URL),
                                    rs.getString(DBConstants.EVENT_NAME), rs.getString(DBConstants.EVENT_TYPE), System.currentTimeMillis(),
                                    rs.getBoolean(DBConstants.NEEDS_AUTH0_TOKEN), rs.getString(DBConstants.UPLOAD_REASON),
                                    rs.getString(DBConstants.DDP_KIT_REQUEST_ID)));
                        }
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            logger.error("Error getting list of kit requests which aren't received yet (for reminder emails) ", results.resultException);
        }
        logger.info("Found " + kitDDPNotifications.size() + " kit requests for which the ddp needs to trigger reminder emails.");
        return kitDDPNotifications;
    }
}
