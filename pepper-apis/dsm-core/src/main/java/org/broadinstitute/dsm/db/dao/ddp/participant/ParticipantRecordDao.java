package org.broadinstitute.dsm.db.dao.ddp.participant;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantRecordDto;

public class ParticipantRecordDao implements Dao<ParticipantRecordDto> {

    private static final String SQL_INSERT_PARTICIPANT = "INSERT INTO ddp_participant_record SET " +
            "participant_id = ?, " +
            "cr_sent = ?, " +
            "cr_received = ?, " +
            "notes = ?, " +
            "minimal_mr = ?, " +
            "abstraction_ready = ?, " +
            "additional_values_json = ?, " +
            "last_changed = ?, " +
            "changed_by = ? ON DUPLICATE KEY UPDATE last_changed = ?, changed_by = ?";

    @Override
    public int create(ParticipantRecordDto participantRecordDto) {
        SimpleResult simpleResult = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult(-1);
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_PARTICIPANT, Statement.RETURN_GENERATED_KEYS )) {
                stmt.setInt(1, participantRecordDto.getParticipantId());
                stmt.setString(2, participantRecordDto.getCrSent().orElse(null));
                stmt.setString(3, participantRecordDto.getCrReceived().orElse(null));
                stmt.setString(4, participantRecordDto.getNotes().orElse(null));
                stmt.setObject(5, participantRecordDto.getMinimalMr().orElse(null));
                stmt.setObject(6, participantRecordDto.getAbstractionReady().orElse(null));
                stmt.setString(7, participantRecordDto.getAdditionalValuesJson().orElse(null));
                stmt.setLong(8, participantRecordDto.getLastChanged());
                stmt.setString(9, participantRecordDto.getChangedBy().orElse(null));
                stmt.setLong(10, participantRecordDto.getLastChanged());
                stmt.setString(11, participantRecordDto.getChangedBy().orElse(null));
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
            throw new RuntimeException("Error inserting participant record for participant id: " + participantRecordDto.getParticipantId(), simpleResult.resultException);
        }
        return (int) simpleResult.resultValue;
    }

    @Override
    public int delete(int id) {
        return 0;
    }

    @Override
    public Optional<ParticipantRecordDto> get(long id) {
        return Optional.empty();
    }
}
