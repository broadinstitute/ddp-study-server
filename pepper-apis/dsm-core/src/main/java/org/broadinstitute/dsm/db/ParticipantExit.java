package org.broadinstitute.dsm.db;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.Data;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dao.bookmark.BookmarkDao;
import org.broadinstitute.dsm.db.dao.roles.UserRoleDao;
import org.broadinstitute.dsm.db.dto.bookmark.BookmarkDto;
import org.broadinstitute.dsm.db.dto.user.UserRoleDto;
import org.broadinstitute.dsm.model.ddp.DDPParticipant;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.lddp.db.SimpleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Data
public class ParticipantExit {

    private static final Logger logger = LoggerFactory.getLogger(ParticipantExit.class);

    private static final String SQL_SELECT_EXITED_PT =
            "SELECT realm.instance_name, ex.ddp_participant_id, ex.exit_date, ex.in_ddp, ex.exit_by "
                    + "FROM ddp_participant_exit ex, ddp_instance realm WHERE ex.ddp_instance_id = realm.ddp_instance_id "
                    + "AND realm.instance_name = ?";
    private static final String SQL_INSERT_EXIT_PT =
            "INSERT INTO ddp_participant_exit (ddp_instance_id, ddp_participant_id, exit_date, exit_by, in_ddp) " + "VALUES (?,?,?,?,?)";

    private final String realm;
    private final String participantId;
    private String user;
    private long userId;
    private final long exitDate;
    private final boolean inDDP;

    private String shortId;
    private String legacyShortId;

    public ParticipantExit(String realm, String participantId, String user, long exitDate, boolean inDDP, long userId) {
        this.realm = realm;
        this.participantId = participantId;
        this.user = user;
        this.exitDate = exitDate;
        this.inDDP = inDDP;
        this.userId = userId;
    }

    public static Map<String, ParticipantExit> getExitedParticipants(@NonNull String realm) {
        return getExitedParticipants(realm, true);
    }

    public static Map<String, ParticipantExit> getExitedParticipants(@NonNull String realm, boolean addParticipantInformation) {
        Map<String, ParticipantExit> exitedParticipants = new HashMap<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_EXITED_PT)) {
                stmt.setString(1, realm);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String ddpParticipantId = rs.getString(DBConstants.DDP_PARTICIPANT_ID);
                        exitedParticipants.put(ddpParticipantId,
                                new ParticipantExit(rs.getString(DBConstants.INSTANCE_NAME), ddpParticipantId,
                                        null, rs.getLong(DBConstants.EXIT_DATE),
                                        rs.getBoolean(DBConstants.IN_DDP), rs.getLong(DBConstants.EXIT_BY)));
                    }
                }
            } catch (Exception ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            logger.error("Couldn't get exited participants for " + realm, results.resultException);
        } else {
            if (addParticipantInformation) {
                addParticipantInformation(realm, exitedParticipants.values());
            }
        }
        getUserNames(exitedParticipants, realm);
        return exitedParticipants;
    }

    private static void getUserNames(Map<String, ParticipantExit> exitedParticipants, String realm) {
        UserRoleDao userRoleDao = new UserRoleDao();
        DDPInstance ddpInstance = DDPInstance.getDDPInstanceByRealmOrGuid(realm);
        List<UserRoleDto> userList = userRoleDao.getAllUsersWithRoleForRealm(ddpInstance.getStudyGuid());
        Optional<BookmarkDto> maybeUserIdBookmark = new BookmarkDao().getBookmarkByInstance("FIRST_DSM_USER_ID");
        maybeUserIdBookmark.orElseThrow();
        Long firstNewUserId = maybeUserIdBookmark.get().getValue();
        for (String key : exitedParticipants.keySet()) {
            ParticipantExit participantExit = exitedParticipants.get(key);
            long userId = participantExit.userId;
            boolean isLegacy = userId < firstNewUserId;
            if (isLegacy) {
                userList.stream().filter(user -> user.getUser().getDsmLegacyId() == userId).findAny()
                        .ifPresent(u -> participantExit.user = u.getUser().getName().get());
            } else {
                userList.stream().filter(user -> user.getUser().getUserId() == userId).findAny()
                        .ifPresent(u -> participantExit.user = u.getUser().getName().get());
            }
        }
    }

    private static void addParticipantInformation(@NonNull String realm, @NonNull Collection<ParticipantExit> exitedParticipants) {
        DDPInstance instance = DDPInstance.getDDPInstance(realm);
        if (!instance.isHasRole()) {
            if (StringUtils.isNotBlank(instance.getParticipantIndexES())) {
                Map<String, Map<String, Object>> participantsESData =
                        ElasticSearchUtil.getDDPParticipantsFromES(realm, instance.getParticipantIndexES());
                for (ParticipantExit exitParticipant : exitedParticipants) {
                    DDPParticipant ddpParticipant =
                            ElasticSearchUtil.getParticipantAsDDPParticipant(participantsESData, exitParticipant.getParticipantId());
                    if (ddpParticipant != null) {
                        exitParticipant.setShortId(ddpParticipant.getShortId());
                        exitParticipant.setLegacyShortId(ddpParticipant.getLegacyShortId());
                    }
                }
            }
        }
    }

    public static void exitParticipant(@NonNull String ddpParticipantId, @NonNull long currentTime, @NonNull String userId,
                                       @NonNull DDPInstance instance, boolean inDDP) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_EXIT_PT)) {
                stmt.setString(1, instance.getDdpInstanceId());
                stmt.setString(2, ddpParticipantId);
                stmt.setLong(3, currentTime);
                stmt.setString(4, userId);
                stmt.setBoolean(5, inDDP);
                int result = stmt.executeUpdate();
                if (result == 1) {
                    logger.info("Exited participant w/ ddpParticipantId " + ddpParticipantId + " from " + instance.getName());
                } else {
                    throw new RuntimeException("Something is wrong w/ ddpParticipantId " + ddpParticipantId);
                }
            } catch (Exception ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            logger.error("Couldn't exit participant w/ ddpParticipantId " + ddpParticipantId, results.resultException);
        }
    }
}
