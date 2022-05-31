package org.broadinstitute.dsm.db.dao.ddp.participant;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.lddp.db.SimpleResult;

public class ParticipantDao implements Dao<ParticipantDto> {

    private static final String SQL_INSERT_PARTICIPANT =
            "INSERT INTO ddp_participant (ddp_participant_id, last_version, last_version_date, ddp_instance_id, release_completed, "
                    + "assignee_id_mr, assignee_id_tissue, last_changed, changed_by) VALUES (?,?,?,?,?,?,?,?,?) "
                    + "ON DUPLICATE KEY UPDATE last_changed = ?, changed_by = ?";

    private static final String SQL_SELECT_PARTICIPANT_FROM_COLLABORATOR_ID = "SELECT p.ddp_participant_id from ddp_participant p "
            +
            "left join ddp_kit_request req on (req.ddp_participant_id = p.ddp_participant_id) where req.bsp_collaborator_participant_id = ? ";

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

    public Optional<String> getParticipantFromCollaboratorParticipantId(String participantId) {
        SimpleResult simpleResult = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult(-1);
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_PARTICIPANT_FROM_COLLABORATOR_ID)) {
                stmt.setString(1, participantId);
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
            throw new RuntimeException("Error getting participant with collab participant id: " + participantId,
                    simpleResult.resultException);
        }
        return Optional.ofNullable((String) simpleResult.resultValue);
    }
}
