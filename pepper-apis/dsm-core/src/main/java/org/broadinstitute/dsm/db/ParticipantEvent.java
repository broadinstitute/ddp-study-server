package org.broadinstitute.dsm.db;

import lombok.Data;
import lombok.NonNull;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.model.ddp.DDPParticipant;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.util.DDPRequestUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

@Data
public class ParticipantEvent {

    private static final Logger logger = LoggerFactory.getLogger(ParticipantEvent.class);

    private final String participantId;
    private final String eventType;
    private final String user;
    private final long date;

    private String shortId;

    private static String GET_PARTICIPANT_EVENT = "select event " +
            "        from " +
            "        ddp_participant_event ev " +
            "        where " +
            "        ev.ddp_instance_id = ? " +
            "        and ev.ddp_participant_id = ?";

    public ParticipantEvent(String participantId, String eventType, String user, long date) {
        this.participantId = participantId;
        this.eventType = eventType;
        this.user = user;
        this.date = date;
    }

    public static Collection<ParticipantEvent> getSkippedParticipantEvents(@NonNull String realm) {
        ArrayList<ParticipantEvent> skippedParticipantEvents = new ArrayList();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.GET_PARTICIPANT_EVENTS))) {
                stmt.setString(1, realm);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        skippedParticipantEvents.add(new ParticipantEvent(
                                rs.getString(DBConstants.DDP_PARTICIPANT_ID),
                                rs.getString(DBConstants.EVENT), rs.getString(DBConstants.NAME),
                                rs.getLong(DBConstants.DATE)));
                    }
                }
            }
            catch (Exception ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            logger.error("Couldn't get list of skipped participant events for " + realm, results.resultException);
        }
        else {
            DDPInstance instance = DDPInstance.getDDPInstance(realm);
            for (ParticipantEvent skippedParticipant : skippedParticipantEvents) {
                String sendRequest = instance.getBaseUrl() + RoutePath.DDP_PARTICIPANTS_PATH + "/" + skippedParticipant.getParticipantId();
                try {
                    DDPParticipant ddpParticipant = DDPRequestUtil.getResponseObject(DDPParticipant.class, sendRequest, realm, instance.isHasAuth0Token());
                    if (ddpParticipant != null) {
                        skippedParticipant.setShortId(ddpParticipant.getShortId());
                    }
                }
                catch (Exception ioe) {
                    logger.error("Couldn't get shortId of skipped participant from " + sendRequest, ioe);
                }
            }
        }
        return skippedParticipantEvents;
    }

    public static void skipParticipantEvent(@NonNull String ddpParticipantId, @NonNull long currentTime, @NonNull String userId,
                                            @NonNull DDPInstance instance, @NonNull String eventType) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.INSERT_PARTICIPANT_EVENT))) {
                stmt.setString(1, instance.getDdpInstanceId());
                stmt.setString(2, ddpParticipantId);
                stmt.setLong(3, currentTime);
                stmt.setString(4, userId);
                stmt.setString(5, eventType);
                int result = stmt.executeUpdate();
                if (result == 1) {
                    logger.info("Skip event " + eventType + " for participant w/ ddpParticipantId " + ddpParticipantId + " from " + instance.getName());
                }
                else {
                    throw new RuntimeException("Something is wrong w/ ddpParticipantId " + ddpParticipantId);
                }
            }
            catch (Exception ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            logger.error("Couldn't skip event for participant w/ ddpParticipantId " + ddpParticipantId, results.resultException);
        }
    }

    public static Collection<String> getParticipantEvent(@NonNull String ddpParticipantId, @NonNull String instanceId) {
        ArrayList<String> skippedEvents = new ArrayList();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(GET_PARTICIPANT_EVENT)) {
                stmt.setString(1, instanceId);
                stmt.setString(2, ddpParticipantId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        skippedEvents.add(rs.getString(DBConstants.EVENT));
                    }
                }
            }
            catch (Exception ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            logger.error("Couldn't exited participants for " + instanceId, results.resultException);
        }
        return skippedEvents;
    }

    public static Collection<String> getParticipantEvent(Connection conn, @NonNull String ddpParticipantId, @NonNull String instanceId) {
        ArrayList<String> skippedEvents = new ArrayList();
        try (PreparedStatement stmt = conn.prepareStatement(GET_PARTICIPANT_EVENT)) {
            stmt.setString(1, instanceId);
            stmt.setString(2, ddpParticipantId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    skippedEvents.add(rs.getString(DBConstants.EVENT));
                }
            }
        }
        catch (Exception ex) {
            logger.error("Couldn't get exited participants for " + instanceId, ex);
        }

        return skippedEvents;
    }
}

