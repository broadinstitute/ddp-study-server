package org.broadinstitute.dsm.db.dao.ddp.participant;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

import lombok.NonNull;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dao.util.DaoUtil;
import org.broadinstitute.dsm.db.dao.util.ResultsBuilder;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.lddp.db.SimpleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParticipantDao implements Dao<ParticipantDto> {

    private static final Logger logger = LoggerFactory.getLogger(ParticipantDao.class);

    private static final String SQL_INSERT_PARTICIPANT =
            "INSERT INTO ddp_participant (ddp_participant_id, last_version, last_version_date, ddp_instance_id, release_completed, "
                    + "assignee_id_mr, assignee_id_tissue, last_changed, changed_by) VALUES (?,?,?,?,?,?,?,?,?) "
                    + "ON DUPLICATE KEY UPDATE last_changed = ?, changed_by = ?";

    private static final String SQL_SELECT_PARTICIPANT_FROM_COLLABORATOR_ID = "SELECT p.ddp_participant_id from ddp_participant p "
            + "left join ddp_kit_request req on (req.ddp_participant_id = p.ddp_participant_id) "
            + "where req.bsp_collaborator_participant_id = ? and req.ddp_instance_id = ? ";

    private static final String SQL_FILTER_BY_DDP_PARTICIPANT_ID = "ddp_participant_id = ?";
    private static final String SQL_FILTER_BY_DDP_INSTANCE_ID = "ddp_instance_id = ?";
    private static final String SQL_GET_PARTICIPANT_BY_DDP_PARTICIPANT_ID_AND_DDP_INSTANCE_ID = "SELECT * FROM ddp_participant WHERE "
            + SQL_FILTER_BY_DDP_PARTICIPANT_ID + " AND " + SQL_FILTER_BY_DDP_INSTANCE_ID + ";";

    public static final String SQL_SELECT_BY_ID = "SELECT * FROM ddp_participant WHERE participant_id = ?;";

    private static final String SQL_DELETE_BY_ID = "DELETE FROM ddp_participant WHERE participant_id = ?";

    public static ParticipantDao of() {
        return new ParticipantDao();
    }

    @Override
    public int create(ParticipantDto participantDto) {
        logger.info(String.format("Attempting to create a new participant with ddp_participant_id = %s",
                participantDto.getDdpParticipantId().orElse("")));
        SimpleResult simpleResult = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult(-1);
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_PARTICIPANT, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, participantDto.getDdpParticipantId().orElse(null));
                stmt.setObject(2, participantDto.getLastVersion().orElse(null));
                stmt.setString(3, participantDto.getLastVersionDate().orElse(null));
                stmt.setInt(4, participantDto.getDdpInstanceId());
                stmt.setObject(5, participantDto.getReleaseCompleted().orElse(null));
                stmt.setObject(6, participantDto.getAssigneeIdMr().orElse(null));
                stmt.setObject(7, participantDto.getAssigneeIdTissue().orElse(null));
                stmt.setObject(8, participantDto.getAssigneeIdTissue().orElse(null));
                stmt.setLong(8, participantDto.getLastChanged());
                stmt.setObject(9, participantDto.getChangedBy().orElse(null));
                stmt.setLong(10, participantDto.getLastChanged());
                stmt.setObject(11, participantDto.getChangedBy().orElse(null));
                stmt.executeUpdate();
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        dbVals.resultValue = rs.getInt(1);
                    }
                }
            } catch (SQLException sqle) {
                dbVals.resultException = sqle;
            }
            return dbVals;
        });
        if (simpleResult.resultException != null) {
            throw new RuntimeException("Error inserting participant with id: " + participantDto.getDdpParticipantId().orElse(""),
                    simpleResult.resultException);
        }
        logger.info(String.format("A new participant with ddp_participant_id = %s has been created successfully",
                participantDto.getDdpParticipantId().orElse("")));
        return (int) simpleResult.resultValue;
    }

    @Override
    public int delete(int id) {
        SimpleResult simpleResult = DaoUtil.deleteById(id, SQL_DELETE_BY_ID);
        if (simpleResult.resultException != null) {
            throw new RuntimeException("Error deleting participant with id: " + id, simpleResult.resultException);
        }
        return (int) simpleResult.resultValue;
    }

    @Override
    public Optional<ParticipantDto> get(long id) {
        ParticipantDao.BuildParticipant builder = new ParticipantDao.BuildParticipant();
        SimpleResult res = DaoUtil.getById(id, SQL_SELECT_BY_ID, builder);
        if (res.resultException != null) {
            throw new RuntimeException("Error getting participant with id: " + id,
                    res.resultException);
        }
        return (Optional<ParticipantDto>) res.resultValue;
    }

    public Optional<String> getParticipantFromCollaboratorParticipantId(String collaboratorParticipantId, String ddpInstanceId) {
        SimpleResult simpleResult = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult(-1);
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_PARTICIPANT_FROM_COLLABORATOR_ID)) {
                stmt.setString(1, collaboratorParticipantId);
                stmt.setString(2, ddpInstanceId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = rs.getString(1);
                    }
                }
            } catch (SQLException e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });
        if (simpleResult.resultException != null) {
            throw new RuntimeException("Error getting participant with collab participant id: " + collaboratorParticipantId,
                    simpleResult.resultException);
        }
        return Optional.ofNullable((String) simpleResult.resultValue);
    }

    public Optional<ParticipantDto> getParticipantByDdpParticipantIdAndDdpInstanceId(@NonNull String ddpParticipantId, int ddpInstanceId) {
        logger.info(String.format("Attempting to find participant with ddp_participant_id = %s and ddp_instance_id = %s in DB",
                ddpParticipantId, ddpInstanceId));
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult executionResult = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_GET_PARTICIPANT_BY_DDP_PARTICIPANT_ID_AND_DDP_INSTANCE_ID)) {
                stmt.setString(1, ddpParticipantId);
                stmt.setInt(2, ddpInstanceId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        executionResult.resultValue = new ParticipantDao.BuildParticipant().build(rs);
                    }
                }
            } catch (SQLException ex) {
                executionResult.resultException = ex;
            }
            return executionResult;
        });
        if (results.resultException != null) {
            throw new RuntimeException("Error getting participant data with " + ddpParticipantId, results.resultException);
        }
        logger.info(String.format("Got participant with ddp_participant_id = %s and ddp_instance_id = %s",
                ddpParticipantId, ddpInstanceId));
        return Optional.ofNullable((ParticipantDto) results.resultValue);
    }

    private static class BuildParticipant implements ResultsBuilder {

        public Object build(ResultSet rs) throws SQLException {
            return new ParticipantDto.Builder()
                    .withParticipantId(rs.getInt(DBConstants.PARTICIPANT_ID))
                    .withDdpParticipantId(rs.getString(DBConstants.DDP_PARTICIPANT_ID))
                    .withLastVersion(rs.getLong(DBConstants.LAST_VERSION))
                    .withLastVersionDate(rs.getString(DBConstants.LAST_VERSION_DATE))
                    .withDdpInstanceId(rs.getInt(DBConstants.DDP_INSTANCE_ID))
                    .withReleaseCompleted(rs.getBoolean(DBConstants.RELEASE_COMPLETED))
                    .withAssigneeIdMr(rs.getInt(DBConstants.ASSIGNEE_ID_MR))
                    .withAssigneeIdTissue(rs.getInt(DBConstants.ASSIGNEE_ID_TISSUE))
                    .withLastChanged(rs.getLong(DBConstants.LAST_CHANGED))
                    .withChangedBy(rs.getString(DBConstants.CHANGED_BY))
                    .build();
        }
    }


}
