package org.broadinstitute.dsm.db.dao.ddp.participant;

import org.broadinstitute.lddp.db.SimpleResult;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDataDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.stream.Collectors;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

public class ParticipantDataDao implements Dao<ParticipantDataDto> {

    private static final Logger logger = LoggerFactory.getLogger(ParticipantDataDao.class);

    private static final String SQL_PARTICIPANT_DATA_BY_PARTICIPANT_ID = "SELECT " +
            "participant_data_id," +
            "ddp_participant_id," +
            "ddp_instance_id," +
            "field_type_id," +
            "data," +
            "last_changed," +
            "changed_by" +
            " FROM ddp_participant_data WHERE ddp_participant_id = ?";
    private static final String SQL_ALL_PARTICIPANT_DATA = "SELECT " +
            "participant_data_id," +
            "ddp_participant_id," +
            "ddp_instance_id," +
            "field_type_id," +
            "data," +
            "last_changed," +
            "changed_by" +
            " FROM ddp_participant_data ";
    private static final String BY_INSTANCE_ID = "WHERE ddp_instance_id = ? ";
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

    private static final String SQL_UPDATE_DATA_TO_PARTICIPANT_DATA = "UPDATE ddp_participant_data SET data = ?, " +
            "last_changed = ?, changed_by = ? WHERE participant_data_id = ?";

    private static final String SQL_GET_PARTICIPANT_DATA_BY_PARTICIPANT_IDS = SQL_ALL_PARTICIPANT_DATA + "WHERE ddp_participant_id IN (?)";

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
                stmt.setString(1, participantDataDto.getDdpParticipantId().orElse(""));
                stmt.setInt(2, participantDataDto.getDdpInstanceId());
                stmt.setString(3, participantDataDto.getFieldTypeId().orElse(""));
                stmt.setString(4, participantDataDto.getData().orElse(""));
                stmt.setLong(5, participantDataDto.getLastChanged());
                stmt.setString(6, participantDataDto.getChangedBy().orElse("SYSTEM"));
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
            throw new RuntimeException("Error inserting ddp participant data ", simpleResult.resultException);
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
                         execResult.resultValue = new ParticipantDataDto.Builder()
                                 .withParticipantDataId(rs.getInt(PARTICIPANT_DATA_ID))
                                 .withDdpParticipantId(rs.getString(DDP_PARTICIPANT_ID))
                                 .withDdpInstanceId(rs.getInt(DDP_INSTANCE_ID))
                                 .withFieldTypeId(rs.getString(FIELD_TYPE_ID))
                                 .withData(rs.getString(DATA))
                                 .withLastChanged(rs.getLong(LAST_CHANGED))
                                 .withChangedBy(rs.getString(CHANGED_BY))
                                 .build();
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

    public int updateParticipantDataColumn(ParticipantDataDto participantDataDto) {
        SimpleResult result = inTransaction((conn) -> {
            SimpleResult execResult = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_DATA_TO_PARTICIPANT_DATA)) {
                stmt.setString(1, participantDataDto.getData().orElse(""));
                stmt.setLong(2, participantDataDto.getLastChanged());
                stmt.setString(3, participantDataDto.getChangedBy().orElse("SYSTEM"));
                stmt.setInt(4, participantDataDto.getParticipantDataId());
                execResult.resultValue = stmt.executeUpdate();
            } catch (SQLException sqle) {
                execResult.resultException = sqle;
            }
            return execResult;
        });
        if (result.resultException != null) {
            throw new RuntimeException(String.format("Could not update data for participant data with id: %s for participant with guid: %s",
                    participantDataDto.getParticipantDataId(), participantDataDto.getDdpParticipantId()));
        }
        logger.info(String.format("Updated data for participant data with id: %s for participant with guid: %s",
                participantDataDto.getParticipantDataId(), participantDataDto.getDdpParticipantId()));

        return (int) result.resultValue;
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
                            new ParticipantDataDto.Builder()
                                    .withParticipantDataId(rs.getInt(PARTICIPANT_DATA_ID))
                                    .withDdpParticipantId(rs.getString(DDP_PARTICIPANT_ID))
                                    .withDdpInstanceId(rs.getInt(DDP_INSTANCE_ID))
                                    .withFieldTypeId(rs.getString(FIELD_TYPE_ID))
                                    .withData(rs.getString(DATA))
                                    .withLastChanged(rs.getLong(LAST_CHANGED))
                                    .withChangedBy(rs.getString(CHANGED_BY))
                                    .build()
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

    public Map<String, List<ParticipantDataDto>> getParticipantDataByParticipantIds(List<String> participantIds) {
        String sqlWithInClause = SQL_GET_PARTICIPANT_DATA_BY_PARTICIPANT_IDS.replace("?",
                participantIds.stream().collect(Collectors.joining("','", "'", "'")));
        Map<String, List<ParticipantDataDto>> participantDatasByParticipantIds = new HashMap<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult execResult = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(sqlWithInClause)) {
                try(ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        ArrayList<ParticipantDataDto> value = new ArrayList<>(
                                List.of(new ParticipantDataDto.Builder()
                                        .withParticipantDataId(rs.getInt(PARTICIPANT_DATA_ID))
                                        .withDdpParticipantId(rs.getString(DDP_PARTICIPANT_ID))
                                        .withDdpInstanceId(rs.getInt(DDP_INSTANCE_ID))
                                        .withFieldTypeId(rs.getString(FIELD_TYPE_ID))
                                        .withData(rs.getString(DATA))
                                        .withLastChanged(rs.getLong(LAST_CHANGED))
                                        .withChangedBy(rs.getString(CHANGED_BY))
                                        .build()
                                )
                        );
                        participantDatasByParticipantIds.merge(rs.getString(DDP_PARTICIPANT_ID), value, (preValue, currentValue) -> {
                            preValue.addAll(currentValue);
                            return preValue;
                        });
                    }
                }
            }
            catch (SQLException ex) {
                execResult.resultException = ex;
            }
            return execResult;
        });
        if (results.resultException != null) {
            throw new RuntimeException("Error getting participants data with ", results.resultException);
        }
        return participantDatasByParticipantIds;
    }



    public Map<String, List<ParticipantDataDto>> getParticipantDataByInstanceIdAndFilterQuery(int ddpInstanceId, String filterQuery) {
        Map<String, List<ParticipantDataDto>> participantDatasByParticipantIds = new HashMap<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult execResult = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_ALL_PARTICIPANT_DATA + BY_INSTANCE_ID + filterQuery)) {
                stmt.setInt(1, ddpInstanceId);
                try(ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        ArrayList<ParticipantDataDto> value = new ArrayList<>(
                                List.of(new ParticipantDataDto.Builder()
                                        .withParticipantDataId(rs.getInt(PARTICIPANT_DATA_ID))
                                        .withDdpParticipantId(rs.getString(DDP_PARTICIPANT_ID))
                                        .withDdpInstanceId(rs.getInt(DDP_INSTANCE_ID))
                                        .withFieldTypeId(rs.getString(FIELD_TYPE_ID))
                                        .withData(rs.getString(DATA))
                                        .withLastChanged(rs.getLong(LAST_CHANGED))
                                        .withChangedBy(rs.getString(CHANGED_BY))
                                        .build()
                                )
                        );
                        participantDatasByParticipantIds.merge(rs.getString(DDP_PARTICIPANT_ID), value, (preValue, currentValue) -> {
                            preValue.addAll(currentValue);
                            return preValue;
                        });
                    }
                }
            }
            catch (SQLException ex) {
                execResult.resultException = ex;
            }
            return execResult;
        });
        if (results.resultException != null) {
            throw new RuntimeException("Error getting participants data with ", results.resultException);
        }
        return participantDatasByParticipantIds;
    }

    public List<ParticipantDataDto> getAllParticipantData() {
        List<ParticipantDataDto> participantDataDtoList = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult execResult = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_ALL_PARTICIPANT_DATA)) {
                try(ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        participantDataDtoList.add(
                                new ParticipantDataDto.Builder()
                                        .withParticipantDataId(rs.getInt(PARTICIPANT_DATA_ID))
                                        .withDdpParticipantId(rs.getString(DDP_PARTICIPANT_ID))
                                        .withDdpInstanceId(rs.getInt(DDP_INSTANCE_ID))
                                        .withFieldTypeId(rs.getString(FIELD_TYPE_ID))
                                        .withData(rs.getString(DATA))
                                        .withLastChanged(rs.getLong(LAST_CHANGED))
                                        .withChangedBy(rs.getString(CHANGED_BY))
                                        .build()
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
            throw new RuntimeException("Error getting participant data ", results.resultException);
        }
        return participantDataDtoList;
    }

    public List<ParticipantDataDto> getParticipantDataByInstanceId(int instanceId) {
        List<ParticipantDataDto> participantDataDtoList = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult execResult = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_ALL_PARTICIPANT_DATA + BY_INSTANCE_ID)) {
                stmt.setInt(1, instanceId);
                try(ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        participantDataDtoList.add(
                                new ParticipantDataDto.Builder()
                                        .withParticipantDataId(rs.getInt(PARTICIPANT_DATA_ID))
                                        .withDdpParticipantId(rs.getString(DDP_PARTICIPANT_ID))
                                        .withDdpInstanceId(rs.getInt(DDP_INSTANCE_ID))
                                        .withFieldTypeId(rs.getString(FIELD_TYPE_ID))
                                        .withData(rs.getString(DATA))
                                        .withLastChanged(rs.getLong(LAST_CHANGED))
                                        .withChangedBy(rs.getString(CHANGED_BY))
                                        .build()
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
            throw new RuntimeException("Error getting participant data ", results.resultException);
        }
        return participantDataDtoList;
    }


    public List<ParticipantDataDto> getParticipantDataByInstanceIdAndQueryAddition(int instanceId, String queryAddition) {
        List<ParticipantDataDto> participantDataDtoList = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult execResult = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_ALL_PARTICIPANT_DATA + BY_INSTANCE_ID + queryAddition)) {
                stmt.setInt(1, instanceId);
                try(ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        participantDataDtoList.add(
                                new ParticipantDataDto.Builder()
                                        .withParticipantDataId(rs.getInt(PARTICIPANT_DATA_ID))
                                        .withDdpParticipantId(rs.getString(DDP_PARTICIPANT_ID))
                                        .withDdpInstanceId(rs.getInt(DDP_INSTANCE_ID))
                                        .withFieldTypeId(rs.getString(FIELD_TYPE_ID))
                                        .withData(rs.getString(DATA))
                                        .withLastChanged(rs.getLong(LAST_CHANGED))
                                        .withChangedBy(rs.getString(CHANGED_BY))
                                        .build()
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
            throw new RuntimeException("Error getting participant data ", results.resultException);
        }
        return participantDataDtoList;
    }

}
