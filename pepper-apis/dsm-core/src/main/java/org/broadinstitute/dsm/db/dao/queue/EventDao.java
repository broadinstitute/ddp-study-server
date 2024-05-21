package org.broadinstitute.dsm.db.dao.queue;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

import com.sun.istack.NotNull;
import lombok.NonNull;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dto.queue.EventDto;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.KitDDPNotification;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.lddp.db.SimpleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventDao implements Dao<EventDto> {
    private static final Logger logger = LoggerFactory.getLogger(EventDao.class);
    private static String GET_TRIGGERED_EVENT_QUEUE_BY_EVENT_TYPE_AND_DDP_PARTICIPANT_ID = "SELECT "
            + "EVENT_ID, EVENT_DATE_CREATED, EVENT_TYPE, DDP_INSTANCE_ID, DSM_KIT_REQUEST_ID, DDP_PARTICIPANT_ID, EVENT_TRIGGERED "
            + "FROM EVENT_QUEUE " + "WHERE EVENT_TYPE = ? AND DDP_PARTICIPANT_ID = ? AND EVENT_TRIGGERED = 1";

    private static String GET_TRIGGERED_EVENT_QUEUE_BY_EVENT_TYPE_AND_KIT_REQUEST_ID = "SELECT "
            + "EVENT_ID, EVENT_DATE_CREATED, EVENT_TYPE, DDP_INSTANCE_ID, DSM_KIT_REQUEST_ID, DDP_PARTICIPANT_ID, EVENT_TRIGGERED "
            + "FROM EVENT_QUEUE " + "WHERE EVENT_TYPE = ? AND DSM_KIT_REQUEST_ID = ? AND EVENT_TRIGGERED = 1";

    private static final String SQL_INSERT_EVENT =
            "INSERT INTO EVENT_QUEUE SET EVENT_DATE_CREATED = ?, EVENT_TYPE = ?, DDP_INSTANCE_ID = ?, "
                    + "DDP_PARTICIPANT_ID = ?, DSM_KIT_REQUEST_ID = ?, EVENT_TRIGGERED = ?";

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


    @Override
    public int create(EventDto eventDto) {
        throw new NotImplementedException("Not implemented, use insertEvent method instead");
    }

    @Override
    public int delete(int id) {
        throw new NotImplementedException("Not implemented");
    }

    @Override
    public Optional<EventDto> get(long id) {
        throw new NotImplementedException("Not implemented");
    }

    public boolean isEventTriggeredForParticipant(@NotNull String eventType, @NonNull String ddpParticipantId) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(GET_TRIGGERED_EVENT_QUEUE_BY_EVENT_TYPE_AND_DDP_PARTICIPANT_ID,
                    ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_READ_ONLY)) {
                stmt.setString(1, eventType);
                stmt.setString(2, ddpParticipantId);
                try (ResultSet rs = stmt.executeQuery()) {
                    rs.last();
                    int count = rs.getRow();
                    if (count > 0) {
                        dbVals.resultValue = true;
                    } else {
                        dbVals.resultValue = false;
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            logger.error("Couldn't get triggered event for participant " + ddpParticipantId, results.resultException);
        }
        return (boolean) results.resultValue;
    }

    public Optional<Boolean> isEventTriggeredForKit(@NotNull String eventType, @NonNull int dsmKitRequestId) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(GET_TRIGGERED_EVENT_QUEUE_BY_EVENT_TYPE_AND_KIT_REQUEST_ID,
                    ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_READ_ONLY)) {
                stmt.setString(1, eventType);
                stmt.setInt(2, dsmKitRequestId);
                try (ResultSet rs = stmt.executeQuery()) {
                    rs.last();
                    int count = rs.getRow();
                    if (count > 0) {
                        dbVals.resultValue = true;
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            logger.error("Couldn't get triggered event for kit with dsmKitRequestId " + dsmKitRequestId, results.resultException);
        }
        return Optional.ofNullable((Boolean) results.resultValue);
    }

    /**
     * Inserts an event into the EVENT_QUEUE table
     * */
    public void insertEvent(@NonNull String type, @NonNull int ddpInstanceID, String ddpParticipantId,
                                   String dsmKitRequestId, boolean trigger) {
        if (StringUtils.isBlank(ddpParticipantId) && StringUtils.isBlank(dsmKitRequestId)) {
            throw new DsmInternalError(("Both ddpParticipantId and dsmKitRequestId cannot be blank, unable to insert event for type %s and "
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

    /**
     * Creates KitDDPNotification for all the kits that are sent to participants but not returned to Broad
     * (i.e. kits that are not received yet).
     * @return Collection of KitDDPNotification objects
     * */
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
