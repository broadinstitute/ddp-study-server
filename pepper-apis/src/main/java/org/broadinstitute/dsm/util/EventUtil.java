package org.broadinstitute.dsm.util;

import lombok.NonNull;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.db.ParticipantEvent;
import org.broadinstitute.dsm.model.KitDDPNotification;
import org.broadinstitute.dsm.model.TestResultEvent;
import org.broadinstitute.dsm.model.birch.DSMTestResult;
import org.broadinstitute.dsm.model.birch.TestBostonResult;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.RoutePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

public class EventUtil {

    private static final Logger logger = LoggerFactory.getLogger(EventUtil.class);

    public static final String SQL_SELECT_KIT_FOR_REMINDER_EMAILS = "SELECT eve.event_name, eve.event_type, request.ddp_participant_id, request.dsm_kit_request_id, realm.instance_name, realm.base_url, " +
            "realm.ddp_instance_id, realm.auth0_token, realm.notification_recipients, kit.receive_date, kit.scan_date, request.upload_reason, request.ddp_kit_request_id, " +
            "(SELECT count(role.name) FROM ddp_instance realm2, ddp_instance_role inRol, instance_role role " +
            "WHERE realm2.ddp_instance_id = inRol.ddp_instance_id AND inRol.instance_role_id = role.instance_role_id AND role.name = ? AND realm2.ddp_instance_id = realm.ddp_instance_id) AS 'has_role' " +
            "FROM ddp_kit_request request LEFT JOIN ddp_kit kit ON (kit.dsm_kit_request_id = request.dsm_kit_request_id) LEFT JOIN ddp_instance realm ON (request.ddp_instance_id = realm.ddp_instance_id) " +
            "LEFT JOIN ddp_participant_exit ex ON (ex.ddp_participant_id = request.ddp_participant_id AND ex.ddp_instance_id = request.ddp_instance_id) " +
            "LEFT JOIN event_type eve ON (eve.ddp_instance_id = request.ddp_instance_id AND eve.kit_type_id = request.kit_type_id AND eve.event_type = 'REMINDER') " +
            "LEFT JOIN EVENT_QUEUE queue ON (queue.DSM_KIT_REQUEST_ID = request.dsm_kit_request_id AND queue.EVENT_TYPE = eve.event_name) " +
            "WHERE ex.ddp_participant_exit_id IS NULL AND kit.scan_date IS NOT NULL " +
            "AND kit.scan_date <= (UNIX_TIMESTAMP(NOW())-(eve.hours*60*60))*1000 AND kit.receive_date IS NULL AND kit.deactivated_date IS NULL AND realm.is_active = 1 AND queue.EVENT_TYPE IS NULL";
    private static final String SQL_INSERT_EVENT = "INSERT INTO EVENT_QUEUE SET EVENT_DATE_CREATED = ?, EVENT_TYPE = ?, DDP_INSTANCE_ID = ?, DSM_KIT_REQUEST_ID = ?, EVENT_TRIGGERED = ?";

    public void triggerReminder() {
        logger.info("Triggering reminder emails now");
        Collection<KitDDPNotification> kitDDPNotifications = getKitsNotReceived();
        for (KitDDPNotification kitInfo : kitDDPNotifications) {
            if (KitDDPNotification.REMINDER.equals(kitInfo.getEventType())) {
                TransactionWrapper.inTransaction(conn -> {
                    triggerDDP(conn, kitInfo);
                    return null;
                });
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
                            kitDDPNotifications.add(new KitDDPNotification(participantId,
                                    rs.getString(DBConstants.DSM_KIT_REQUEST_ID), rs.getString(DBConstants.DDP_INSTANCE_ID), realm,
                                    rs.getString(DBConstants.BASE_URL),
                                    rs.getString(DBConstants.EVENT_NAME),
                                    rs.getString(DBConstants.EVENT_TYPE), System.currentTimeMillis(),
                                    rs.getBoolean(DBConstants.NEEDS_AUTH0_TOKEN), rs.getString(DBConstants.UPLOAD_REASON),
                                    rs.getString(DBConstants.DDP_KIT_REQUEST_ID)));
                        }
                    }
                }
            }
            catch (SQLException ex) {
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

    public static void triggerDDP(Connection conn,@NonNull KitDDPNotification kitDDPNotification) {
        Collection<String> events = ParticipantEvent.getParticipantEvent(kitDDPNotification.getParticipantId(), kitDDPNotification.getDdpInstanceId());
        if (!events.contains(kitDDPNotification.getEventName())) {
            EventUtil.triggerDDP(conn, kitDDPNotification.getEventName(), kitDDPNotification);
        }
        else {
            logger.info("Participant direct event was added in the participant_event table. DDP will not get triggered");
            //to add these events also to the event table, but without triggering the ddp and flag EVENT_TRIGGERED = false
            addEvent(conn, kitDDPNotification.getEventName(), kitDDPNotification.getDdpInstanceId(), kitDDPNotification.getDsmKitRequestId(), false);
        }
    }

    public static void triggerDDPWithTestResult(Connection conn,@NonNull KitDDPNotification kitDDPNotification, @Nonnull TestBostonResult result) {
        Collection<String> events = ParticipantEvent.getParticipantEvent(kitDDPNotification.getParticipantId(), kitDDPNotification.getDdpInstanceId());
        if (!events.contains(kitDDPNotification.getEventName())) {
            EventUtil.triggerDDPWithTestResult(conn, kitDDPNotification.getEventName(), kitDDPNotification, result);
        }
        else {
            logger.info("Participant direct event was added in the participant_event table. DDP will not get triggered");
            //to add these events also to the event table, but without triggering the ddp and flag EVENT_TRIGGERED = false
            addEvent(conn, kitDDPNotification.getEventName(), kitDDPNotification.getDdpInstanceId(), kitDDPNotification.getDsmKitRequestId(), false);
        }
    }

    private static void triggerDDP(Connection conn, @NonNull String eventType, @NonNull KitDDPNotification kitInfo) {
        try {
            KitEvent event = new KitEvent(kitInfo.getParticipantId(), eventType, kitInfo.getDate() / 1000, kitInfo.getUploadReason(), kitInfo.getDdpKitRequestId());
            String sendRequest = kitInfo.getBaseUrl() + RoutePath.DDP_PARTICIPANT_EVENT_PATH + "/" + kitInfo.getParticipantId();
            DDPRequestUtil.postRequest(sendRequest, event, kitInfo.getInstanceName(), kitInfo.isHasAuth0Token());
            addEvent(conn, eventType, kitInfo.getDdpInstanceId(), kitInfo.getDsmKitRequestId());
        }
        catch (IOException e) {
            logger.error("Failed to trigger " + kitInfo.getInstanceName() + " to notify participant " +  kitInfo.getParticipantId() + " about " + eventType + " for dsm_kit_request_id " + kitInfo.getDsmKitRequestId());
            //to add these events also to the event table, but without triggering the ddp and flag EVENT_TRIGGERED = false
            addEvent(conn, eventType, kitInfo.getDdpInstanceId(), kitInfo.getDsmKitRequestId(), false);
        }
        catch (RuntimeException e) {
            logger.error("Failed to trigger " + kitInfo.getInstanceName() + " to notify participant " +  kitInfo.getParticipantId() + " about " + eventType + " for dsm_kit_request_id " + kitInfo.getDsmKitRequestId());
            //to add these events also to the event table, but without triggering the ddp and flag EVENT_TRIGGERED = false
            addEvent(conn, eventType, kitInfo.getDdpInstanceId(), kitInfo.getDsmKitRequestId(), false);
        }
    }

    private static void triggerDDPWithTestResult(Connection conn,@NonNull String eventType, @NonNull KitDDPNotification kitInfo, @Nonnull TestBostonResult result) {
        try {
            DSMTestResult dsmTestResult = new DSMTestResult(result.getResult(), result.getTimeCompleted(), result.isCorrected());
            TestResultEvent event = new TestResultEvent(kitInfo.getParticipantId(), eventType, kitInfo.getDate() / 1000,kitInfo.getUploadReason(), kitInfo.getDdpKitRequestId(),  dsmTestResult);
            String sendRequest = kitInfo.getBaseUrl() + RoutePath.DDP_PARTICIPANT_EVENT_PATH + "/" + kitInfo.getParticipantId();
            DDPRequestUtil.postRequest(sendRequest, event, kitInfo.getInstanceName(), kitInfo.isHasAuth0Token());
            addEvent(conn, eventType, kitInfo.getDdpInstanceId(), kitInfo.getDsmKitRequestId());
        }
        catch (IOException e) {
            logger.error("Failed to trigger DDP to notify participant about " + eventType);
            //to add these events also to the event table, but without triggering the ddp and flag EVENT_TRIGGERED = false
            addEvent(conn, eventType, kitInfo.getDdpInstanceId(), kitInfo.getDsmKitRequestId(), false);
        }
    }

    private static void addEvent(Connection conn, @NonNull String type, @NonNull String instanceID, @NonNull String requestId) {
        addEvent(conn, type, instanceID, requestId, true);
    }

    public static void addEvent(Connection conn, @NonNull String type, @NonNull String instanceID, @NonNull String requestId, boolean trigger) {
        try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_EVENT)) {
            stmt.setLong(1, System.currentTimeMillis());
            stmt.setString(2, type);
            stmt.setString(3, instanceID);
            stmt.setString(4, requestId);
            stmt.setBoolean(5, trigger);
            int result = stmt.executeUpdate();
            if (result != 1) {
                throw new RuntimeException("Error could not add event for kit request " + requestId);
            }
        }
        catch (SQLException e) {
            logger.error("Error inserting event ", e);
        }
    }
}
