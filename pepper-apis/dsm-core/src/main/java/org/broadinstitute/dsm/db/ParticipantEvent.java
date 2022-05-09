package org.broadinstitute.dsm.db;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import lombok.Data;
import lombok.NonNull;
import org.broadinstitute.dsm.db.dao.bookmark.BookmarkDao;
import org.broadinstitute.dsm.db.dao.user.UserDao;
import org.broadinstitute.dsm.db.dto.bookmark.BookmarkDto;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.dsm.model.ddp.DDPParticipant;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.util.DDPRequestUtil;
import org.broadinstitute.dsm.util.DSMConfig;
import org.broadinstitute.lddp.db.SimpleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Data
public class ParticipantEvent {

    private static final Logger logger = LoggerFactory.getLogger(ParticipantEvent.class);
    private static String GET_PARTICIPANT_EVENT =
            "select event  from ddp_participant_event ev where ev.ddp_instance_id = ? "
                    + "and ev.ddp_participant_id = ?";
    private final String participantId;
    private final String eventType;
    private final long userId;
    private String user;
    private final long date;
    private String shortId;

    public ParticipantEvent(String participantId, String eventType, String user, long date, long userId) {
        this.participantId = participantId;
        this.eventType = eventType;
        this.user = user;
        this.date = date;
        this.userId = userId;
    }

    public static Collection<ParticipantEvent> getSkippedParticipantEvents(@NonNull String realm) {
        ArrayList<ParticipantEvent> skippedParticipantEvents = new ArrayList();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(
                    DSMConfig.getSqlFromConfig(ApplicationConfigConstants.GET_PARTICIPANT_EVENTS))) {
                stmt.setString(1, realm);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        skippedParticipantEvents.add(
                                new ParticipantEvent(rs.getString(DBConstants.DDP_PARTICIPANT_ID), rs.getString(DBConstants.EVENT),
                                        null, rs.getLong(DBConstants.DATE), rs.getLong(DBConstants.DONE_BY)));
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
            for (ParticipantEvent skippedParticipant : skippedParticipantEvents) {
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
        getUserNames(skippedParticipantEvents);
        return skippedParticipantEvents;
    }

    private static void getUserNames(Collection<ParticipantEvent> skippedParticipantEvents) {
        UserDao userDao = new UserDao();
        List<UserDto> userList = userDao.getAllDSMUsers();
        Optional<BookmarkDto> maybeUserIdBookmark = new BookmarkDao().getBookmarkByInstance("FIRST_DSM_USER_ID");
        maybeUserIdBookmark.orElseThrow();
        Long firstNewUserId = maybeUserIdBookmark.get().getValue();
        skippedParticipantEvents.stream().forEach(participantEvent -> {
            long userId = participantEvent.userId;
            boolean isLegacy = userId < firstNewUserId;
            if (isLegacy) {
                userList.stream().filter(user -> user.getDsmLegacyId() == userId).findAny()
                        .ifPresent(u -> participantEvent.user = u.getName().get());
            } else {
                userList.stream().filter(user -> user.getUserId() == userId).findAny()
                        .ifPresent(u -> participantEvent.user = u.getName().get());
            }
        });
    }

    public static void skipParticipantEvent(@NonNull String ddpParticipantId, @NonNull long currentTime, @NonNull String userId,
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
            } catch (Exception ex) {
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
        } catch (Exception ex) {
            logger.error("Couldn't get exited participants for " + instanceId, ex);
        }

        return skippedEvents;
    }
}
