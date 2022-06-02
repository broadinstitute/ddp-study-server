package org.broadinstitute.dsm.db.dao.ddp.participant;

import lombok.NonNull;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.lddp.db.SimpleResult;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;
import static org.broadinstitute.dsm.statics.DBConstants.*;

public class ParticipantDao implements Dao<ParticipantDto> {


    private static final String SQL_INSERT_PARTICIPANT =
            "INSERT INTO ddp_participant (ddp_participant_id, last_version, last_version_date, ddp_instance_id, release_completed, "
                    + "assignee_id_mr, assignee_id_tissue, last_changed, changed_by) VALUES (?,?,?,?,?,?,?,?,?) "
                    + "ON DUPLICATE KEY UPDATE last_changed = ?, changed_by = ?";

    private static final String SQL_FILTER_BY_DDP_PARTICIPANT_ID = "ddp_participant_id = ?";
    private static final String SQL_FILTER_BY_DDP_INSTANCE_ID = "ddp_instance_id = ?";
    private static final String SQL_GET_PARTICIPANT_BY_DDP_PARTICIPANT_ID_AND_DDP_INSTANCE_ID = "SELECT * FROM ddp_participant WHERE " + SQL_FILTER_BY_DDP_PARTICIPANT_ID + " AND " + SQL_FILTER_BY_DDP_INSTANCE_ID + ";";

    private static ParticipantDao participantDao;

    public static ParticipantDao of() {
        if (participantDao == null) {
            participantDao = new ParticipantDao();
        }
        return participantDao;
    }

    @Override
    public int create(ParticipantDto participantDto) {
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
        return (int) simpleResult.resultValue;
    }

    @Override
    public int delete(int id) {
        return 0;
    }

    @Override
    public Optional<ParticipantDto> get(long id) {
        return Optional.empty();
    }

    public Optional<ParticipantDto> getParticipantByDdpParticipantIdAndDdpInstanceId(@NonNull String ddpParticipantId, int ddpInstanceId) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult executionResult = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_GET_PARTICIPANT_BY_DDP_PARTICIPANT_ID_AND_DDP_INSTANCE_ID)) {
                stmt.setString(1, ddpParticipantId);
                stmt.setInt(2, ddpInstanceId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        executionResult.resultValue = buildParticipantFromResultSet(rs);
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
        return Optional.ofNullable((ParticipantDto) results.resultValue);
    }

    private ParticipantDto buildParticipantFromResultSet(ResultSet rs) throws SQLException {
        return new ParticipantDto.Builder()
                .withParticipantId(rs.getInt(PARTICIPANT_ID))
                .withDdpParticipantId(rs.getString(DDP_PARTICIPANT_ID))
                .withLastVersion(rs.getLong(LAST_VERSION))
                .withLastVersionDate(rs.getString(LAST_VERSION_DATE))
                .withDdpInstanceId(rs.getInt(DDP_INSTANCE_ID))
                .withReleaseCompleted(rs.getBoolean(RELEASE_COMPLETED))
                .withAssigneeIdMr(rs.getInt(ASSIGNEE_ID_MR))
                .withAssigneeIdTissue(rs.getInt(ASSIGNEE_ID_TISSUE))
                .withLastChanged(rs.getLong(LAST_CHANGED))
                .withChangedBy(rs.getString(CHANGED_BY))
                .build();
    }

}
