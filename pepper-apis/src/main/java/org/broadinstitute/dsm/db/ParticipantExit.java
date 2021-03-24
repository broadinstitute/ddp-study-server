package org.broadinstitute.dsm.db;

import lombok.Data;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.model.ddp.DDPParticipant;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

@Data
public class ParticipantExit {

    private static final Logger logger = LoggerFactory.getLogger(ParticipantExit.class);

    private static final String SQL_SELECT_EXITED_PT = "SELECT realm.instance_name, ex.ddp_participant_id, u.name, ex.exit_date, ex.in_ddp " +
            "FROM ddp_participant_exit ex, ddp_instance realm, access_user u WHERE ex.ddp_instance_id = realm.ddp_instance_id " +
            "AND ex.exit_by = u.user_id AND realm.instance_name = ?";
    private static final String SQL_INSERT_EXIT_PT = "INSERT INTO ddp_participant_exit (ddp_instance_id, ddp_participant_id, exit_date, exit_by, in_ddp) " +
            "VALUES (?,?,?,?,?)";

    private final String realm;
    private final String participantId;
    private final String user;
    private final long exitDate;
    private final boolean inDDP;

    private String shortId;
    private String legacyShortId;

    public ParticipantExit(String realm, String participantId, String user, long exitDate, boolean inDDP) {
        this.realm = realm;
        this.participantId = participantId;
        this.user = user;
        this.exitDate = exitDate;
        this.inDDP = inDDP;
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
                        exitedParticipants.put(ddpParticipantId, new ParticipantExit(rs.getString(DBConstants.INSTANCE_NAME),
                                ddpParticipantId,
                                rs.getString(DBConstants.NAME), rs.getLong(DBConstants.EXIT_DATE),
                                rs.getBoolean(DBConstants.IN_DDP)));
                    }
                }
            }
            catch (Exception ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            logger.error("Couldn't get exited participants for " + realm, results.resultException);
        }
        else {
            if (addParticipantInformation) {
                addParticipantInformation(realm, exitedParticipants.values());
            }
        }
        return exitedParticipants;
    }

    private static void addParticipantInformation(@NonNull String realm, @NonNull Collection<ParticipantExit> exitedParticipants) {
        DDPInstance instance = DDPInstance.getDDPInstance(realm);
        if (!instance.isHasRole()) {
            if (StringUtils.isNotBlank(instance.getParticipantIndexES())) {
                Map<String, Map<String, Object>> participantsESData = ElasticSearchUtil.getDDPParticipantsFromES(realm, instance.getParticipantIndexES());
                for (ParticipantExit exitParticipant : exitedParticipants) {
                    DDPParticipant ddpParticipant = ElasticSearchUtil.getParticipantAsDDPParticipant(participantsESData, exitParticipant.getParticipantId());
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
            logger.error("Couldn't exit participant w/ ddpParticipantId " + ddpParticipantId, results.resultException);
        }
    }
}
