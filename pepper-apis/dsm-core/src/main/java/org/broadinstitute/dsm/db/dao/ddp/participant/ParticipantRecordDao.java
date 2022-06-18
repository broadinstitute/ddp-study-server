package org.broadinstitute.dsm.db.dao.ddp.participant;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantRecordDto;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.lddp.db.SimpleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParticipantRecordDao implements Dao<ParticipantRecordDto> {

    private static final Logger logger = LoggerFactory.getLogger(ParticipantRecordDao.class);

    private static final String SQL_INSERT_PARTICIPANT =
            "INSERT INTO ddp_participant_record SET participant_id = ?, cr_sent = ?, cr_received = ?, notes = ?, "
                    + "minimal_mr = ?, abstraction_ready = ?, additional_values_json = ?, last_changed = ?, "
                    + "changed_by = ? ON DUPLICATE KEY UPDATE last_changed = ?, changed_by = ?";

    public static final String SQL_FILTER_BY_PARTICIPANT_ID = " WHERE participant_id = ?";

    private static final String SQL_GET_PARTICIPANT_RECORD_DTO_BY_PARTICIPANT_ID = "SELECT * FROM ddp_participant_record"
            + SQL_FILTER_BY_PARTICIPANT_ID + ";";

    public static ParticipantRecordDao of() {
        return new ParticipantRecordDao();
    }

    @Override
    public int create(ParticipantRecordDto participantRecordDto) {
        logger.info(String.format("Attempting to create a new participant_record with participant_id = %s",
                participantRecordDto.getParticipantId()));
        SimpleResult simpleResult = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult(-1);
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_PARTICIPANT, Statement.RETURN_GENERATED_KEYS)) {
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
            throw new RuntimeException("Error inserting participant record for participant id: " + participantRecordDto.getParticipantId(),
                    simpleResult.resultException);
        }
        logger.info(String.format("A new participant_record with participant_id = %s has been created successfully",
                participantRecordDto.getParticipantId()));
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

    public Optional<ParticipantRecordDto> getParticipantRecordByParticipantId(int participantId) {
        logger.info(String.format("Attempting to find participant_record in DB with participant_id = %s", participantId));
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult executionResult = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_GET_PARTICIPANT_RECORD_DTO_BY_PARTICIPANT_ID)) {
                stmt.setInt(1, participantId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        executionResult.resultValue = buildParticipantRecordFromResultSet(rs);
                    }
                }
            } catch (SQLException ex) {
                executionResult.resultException = ex;
            }
            return executionResult;
        });
        if (results.resultException != null) {
            throw new RuntimeException("Error getting participant record with " + participantId, results.resultException);
        }
        logger.info(String.format("Got participant_record from DB with participant_id = %s", participantId));
        return Optional.ofNullable((ParticipantRecordDto) results.resultValue);
    }

    private ParticipantRecordDto buildParticipantRecordFromResultSet(ResultSet rs) throws SQLException {
        return new ParticipantRecordDto.Builder()
                .withParticipantRecordId(rs.getInt(DBConstants.PARTICIPANT_RECORD_ID))
                .withCrSent(rs.getString(DBConstants.CR_SENT))
                .withCrReceived(rs.getString(DBConstants.CR_RECEIVED))
                .withNotes(rs.getString(DBConstants.NOTES))
                .withMinimalMr(rs.getInt(DBConstants.MINIMAL_MR))
                .withAbstractionReady(rs.getInt(DBConstants.ABSTRACTION_READY))
                .withAdditionalValuesJson(rs.getString(DBConstants.ADDITIONAL_VALUES_JSON))
                .withChangedBy(rs.getString(DBConstants.CHANGED_BY))
                .build();
    }

}
