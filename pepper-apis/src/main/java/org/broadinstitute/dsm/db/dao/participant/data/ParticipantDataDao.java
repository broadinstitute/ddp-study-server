package org.broadinstitute.dsm.db.dao.participant.data;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dto.participant.data.ParticipantDataDto;

public class ParticipantDataDao implements Dao<ParticipantDataDto> {

    private static final String SQL_PARTICIPANT_DATA_BY_PARTICIPANT_ID = "SELECT " +
            "participant_data_id," +
            "ddp_participant_id," +
            "ddp_instance_id," +
            "field_type_id," +
            "data," +
            "last_changed," +
            "changed_by" +
            " FROM ddp_participant_data WHERE ddp_participant_id = ?";
    private static final String SQL_DELETE_DDP_PARTICIPANT_DATA = "DELETE FROM ddp_participant_data WHERE participant_data_id = ?";
    private static final String SQL_PARTICIPANT_DATA_BY_ID = "SELECT " +
            "participant_data_id," +
            "ddp_participant_id," +
            "ddp_instance_id," +
            "field_type_id," +
            "data," +
            "last_changed," +
            "changed_by" +
            " FROM ddp_participant_data WHERE participant_data_id = ?";
    private static final String SQL_INSERT_DATA_TO_PARTICIPANT_DATA = "INSERT INTO ddp_participant_data SET " +
            "ddp_participant_id = ?," +
            "ddp_instance_id = ?," +
            "field_type_id = ?," +
            "data = ?," +
            "last_changed = ?," +
            "changed_by = ?";
    private static final String PARTICIPANT_DATA_ID = "participant_data_id";
    private static final String DDP_PARTICIPANT_ID = "ddp_participant_id";
    private static final String DDP_INSTANCE_ID = "ddp_instance_id";
    private static final String FIELD_TYPE_ID = "field_type_id";
    private static final String DATA = "data";
    private static final String LAST_CHANGED = "last_changed";
    private static final String CHANGED_BY = "changed_by";


    @Override
    public int create(ParticipantDataDto participantDataDto) {
        SimpleResult simpleResult = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult(-1);
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_DATA_TO_PARTICIPANT_DATA, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, participantDataDto.getDdpParticipantId());
                stmt.setInt(2, participantDataDto.getDdpInstanceId());
                stmt.setString(3, participantDataDto.getFieldTypeId());
                stmt.setString(4, participantDataDto.getData());
                stmt.setLong(5, participantDataDto.getLastChanged());
                stmt.setString(6, participantDataDto.getChangedBy());
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
            throw new RuntimeException("Error inserting ddp instance ", simpleResult.resultException);
        }
        return (int) simpleResult.resultValue;

    }

    @Override
    public int delete(int id) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult execResult = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_DELETE_DDP_PARTICIPANT_DATA)) {
                stmt.setInt(1, id);
                execResult.resultValue = stmt.executeUpdate();
            }
            catch (SQLException ex) {
                execResult.resultException = ex;
            }
            return execResult;
        });
        if (results.resultException != null) {
            throw new RuntimeException("Error deleting participant data with "
                    + id, results.resultException);
        }
        return (int) results.resultValue;
    }

    @Override
    public Optional<ParticipantDataDto> get(long id) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult execResult = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_PARTICIPANT_DATA_BY_ID)) {
                stmt.setLong(1, id);
                try(ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                         execResult.resultValue = new ParticipantDataDto(
                                        rs.getInt(PARTICIPANT_DATA_ID),
                                        rs.getString(DDP_PARTICIPANT_ID),
                                        rs.getInt(DDP_INSTANCE_ID),
                                        rs.getString(FIELD_TYPE_ID),
                                        rs.getString(DATA),
                                        rs.getLong(LAST_CHANGED),
                                        rs.getString(CHANGED_BY)
                         );
                    }
                }
            }
            catch (SQLException ex) {
                execResult.resultException = ex;
            }
            return execResult;
        });
        if (results.resultException != null) {
            throw new RuntimeException("Error getting participant data with "
                    + id, results.resultException);
        }
        return Optional.ofNullable((ParticipantDataDto) results.resultValue);
    }

    public List<ParticipantDataDto> getParticipantDataByParticipantId(String participantId) {
        List<ParticipantDataDto> participantDataDtoList = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult execResult = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_PARTICIPANT_DATA_BY_PARTICIPANT_ID)) {
                stmt.setString(1, participantId);
                try(ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        participantDataDtoList.add(
                                new ParticipantDataDto(
                                        rs.getInt(PARTICIPANT_DATA_ID),
                                        rs.getString(DDP_PARTICIPANT_ID),
                                        rs.getInt(DDP_INSTANCE_ID),
                                        rs.getString(FIELD_TYPE_ID),
                                        rs.getString(DATA),
                                        rs.getLong(LAST_CHANGED),
                                        rs.getString(CHANGED_BY)
                                )
                        );
                    }
                }
            }
            catch (SQLException ex) {
                execResult.resultException = ex;
            }
            return execResult;
        });
        if (results.resultException != null) {
            throw new RuntimeException("Error getting participant data with "
                    + participantId, results.resultException);
        }
        return participantDataDtoList;
    }
}
