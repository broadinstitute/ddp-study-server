package org.broadinstitute.dsm.db.dao;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;

import lombok.Data;
import lombok.NonNull;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dto.queue.SkippedParticipantEventDto;
import org.broadinstitute.dsm.model.ddp.DDPParticipant;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.util.DDPRequestUtil;
import org.broadinstitute.dsm.util.DSMConfig;
import org.broadinstitute.lddp.db.SimpleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * When a participant no longer wants to get emails or reminders about their kits, they ask the study's CRC, and they enter
 * the participant's ID and the event type into the DSM, which is stored in the ddp_participant_event table.
 * */
@Data
public class SkippedParticipantEventDao {

    private static final Logger logger = LoggerFactory.getLogger(SkippedParticipantEventDao.class);
    private static String GET_PARTICIPANT_EVENT =
            "select event  from ddp_participant_event ev where ev.ddp_instance_id = ? "
                    + "and ev.ddp_participant_id = ?";

    public Collection<SkippedParticipantEventDto> getSkippedParticipantEvents(@NonNull String realm) {
        ArrayList<SkippedParticipantEventDto> skippedParticipantEvents = new ArrayList();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(
                    DSMConfig.getSqlFromConfig(ApplicationConfigConstants.GET_PARTICIPANT_EVENTS))) {
                stmt.setString(1, realm);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        skippedParticipantEvents.add(
                                new SkippedParticipantEventDto(rs.getString(DBConstants.DDP_PARTICIPANT_ID),
                                        rs.getString(DBConstants.EVENT), rs.getString(DBConstants.NAME), rs.getLong(DBConstants.DATE)));
                    }
                }
            } catch (Exception ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            logger.error("Couldn't get list of skipped participant events for " + realm, results.resultException);
        } else {
            DDPInstance instance = DDPInstance.getDDPInstance(realm);
            for (SkippedParticipantEventDto skippedParticipant : skippedParticipantEvents) {
                String sendRequest = instance.getBaseUrl() + RoutePath.DDP_PARTICIPANTS_PATH + "/" + skippedParticipant.getParticipantId();
                try {
                    DDPParticipant ddpParticipant =
                            DDPRequestUtil.getResponseObject(DDPParticipant.class, sendRequest, realm, instance.isHasAuth0Token());
                    if (ddpParticipant != null) {
                        skippedParticipant.setShortId(ddpParticipant.getShortId());
                    }
                } catch (Exception ioe) {
                    logger.error("Couldn't get shortId of skipped participant from " + sendRequest, ioe);
                }
            }
        }
        return skippedParticipantEvents;
    }

    public Collection<String> getSkippedParticipantEvents(@NonNull String ddpParticipantId, int instanceId) {
        ArrayList<String> skippedEvents = new ArrayList();
        SimpleResult result = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(GET_PARTICIPANT_EVENT)) {
                stmt.setInt(1, instanceId);
                stmt.setString(2, ddpParticipantId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        skippedEvents.add(rs.getString(DBConstants.EVENT));
                    }
                }
            } catch (Exception ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });
        if (result.resultException != null) {
            logger.error("Couldn't get skipped participant events for " + instanceId, result.resultException);
        }
        return skippedEvents;
    }

    public boolean isParticipantEventSkipped(@NonNull String ddpParticipantId, @NonNull String eventType,
                                                    int ddpInstanceId) {
        Collection<String> skippedParticipantEvents = getSkippedParticipantEvents(ddpParticipantId, ddpInstanceId);
        return skippedParticipantEvents.contains(eventType);
    }

    public void skipParticipantEvent(@NonNull String ddpParticipantId, @NonNull long currentTime, @NonNull String userId,
                                            @NonNull DDPInstance instance, @NonNull String eventType) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(
                    DSMConfig.getSqlFromConfig(ApplicationConfigConstants.INSERT_PARTICIPANT_EVENT))) {
                stmt.setString(1, instance.getDdpInstanceId());
                stmt.setString(2, ddpParticipantId);
                stmt.setLong(3, currentTime);
                stmt.setString(4, userId);
                stmt.setString(5, eventType);
                int result = stmt.executeUpdate();
                if (result == 1) {
                    logger.info("Skip event " + eventType + " for participant w/ ddpParticipantId " + ddpParticipantId + " from "
                            + instance.getName());
                } else {
                    throw new RuntimeException("Something is wrong w/ ddpParticipantId " + ddpParticipantId);
                }
            } catch (Exception ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            logger.error("Couldn't skip event for participant w/ ddpParticipantId " + ddpParticipantId, results.resultException);
        }
    }
}
