package org.broadinstitute.dsm.db;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.NonNull;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.db.structure.ColumnName;
import org.broadinstitute.dsm.db.structure.TableName;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.DBUtil;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

@Data
@TableName(
        name = DBConstants.DDP_PARTICIPANT_DATA,
        alias = DBConstants.DDP_PARTICIPANT_DATA_ALIAS,
        primaryKey = DBConstants.PARTICIPANT_DATA_ID,
        columnPrefix = "")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ParticipantData {

    private static final Logger logger = LoggerFactory.getLogger(ParticipantData.class);

    public static final String SQL_SELECT_PARTICIPANT = "SELECT d.participant_data_id, d.ddp_participant_id, d.field_type_id, d.data " +
            "FROM ddp_participant_data d " +
            "LEFT JOIN ddp_instance realm on (d.ddp_instance_id = realm.ddp_instance_id) " +
            "WHERE realm.instance_name = ? ";

    public static final String SQL_INSERT_PARTICIPANT = "INSERT INTO ddp_participant_data SET " +
            "ddp_participant_id = ?, ddp_instance_id = ?, field_type_id = ?, data = ?, last_changed = ?, changed_by = ? ";

    @ColumnName(DBConstants.PARTICIPANT_DATA_ID)
    private long participantDataId;

    @ColumnName(DBConstants.DDP_PARTICIPANT_ID)
    private long ddpParticipantId;

    @ColumnName(DBConstants.FIELD_TYPE_ID)
    private String fieldTypeId;

    @ColumnName (DBConstants.DATA)
    @JsonProperty("dynamicFields")
    @SerializedName("dynamicFields")
    private String data;

    @JsonProperty("dynamicFields")
    public Map<String, Object> getDynamicFields() {
        try {
            return ObjectMapperSingleton.instance().readValue(data, new TypeReference<Map<String, Object>>() {});
        } catch (IOException | NullPointerException e) {
            return Map.of();
        }
    }

    public ParticipantData() {

    }

    public ParticipantData(long dataId, String fieldTypeId, String data) {
        this.participantDataId = dataId;
        this.fieldTypeId = fieldTypeId;
        this.data = data;
    }

    public static ParticipantData getParticipantDataObject(@NonNull ResultSet rs) throws SQLException {
        ParticipantData participantData = new ParticipantData(
                rs.getLong(DBConstants.PARTICIPANT_DATA_ID),
                rs.getString(DBConstants.FIELD_TYPE_ID),
                rs.getString(DBConstants.DATA)
        );
        return participantData;
    }

    public static Map<String, List<ParticipantData>> getParticipantData(@NonNull String realm) {
        return getParticipantData(realm, null);
    }

    public static Map<String, List<ParticipantData>> getParticipantData(@NonNull String realm, String queryAddition) {
        logger.info("Collection participant data information");
        Map<String, List<ParticipantData>> participantData = new HashMap<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(DBUtil.getFinalQuery(SQL_SELECT_PARTICIPANT, queryAddition))) {
                stmt.setString(1, realm);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String ddpParticipantId = rs.getString(DBConstants.DDP_PARTICIPANT_ID);
                        List<ParticipantData> participantDataList = new ArrayList<>();
                        if (participantData.containsKey(ddpParticipantId)) {
                            participantDataList = participantData.get(ddpParticipantId);
                        }
                        else {
                            participantData.put(ddpParticipantId, participantDataList);
                        }
                        participantDataList.add(getParticipantDataObject(rs));
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Couldn't get list of participant data ", results.resultException);
        }
        logger.info("Got " + participantData.size() + " participants data in DSM DB for " + realm);
        return participantData;
    }

    public static String createNewParticipantData(@NonNull String ddpParticipantId, @NonNull String ddpInstanceId, @NonNull String fieldTypeId,
                                                  @NonNull String data, @NonNull String user){
        SimpleResult results = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_PARTICIPANT, Statement.RETURN_GENERATED_KEYS)){
                stmt.setString(1, ddpParticipantId);
                stmt.setString(2, ddpInstanceId);
                stmt.setString(3, fieldTypeId);
                stmt.setString(4, data);
                stmt.setLong(5, System.currentTimeMillis());
                stmt.setString(6, user);
                int result = stmt.executeUpdate();
                if (result == 1){
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            logger.info("Added participant data for " + ddpParticipantId);
                            dbVals.resultValue = rs.getString(1);
                        }
                    }
                    catch (Exception e) {
                        throw new RuntimeException("Error getting id of new institution ", e);
                    }
                }
                else {
                    throw new RuntimeException("Error adding participant data for " + ddpParticipantId + ": it was updating " + result + " rows");
                }
            }
            catch (SQLException ex){
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null){
            throw new RuntimeException("Error adding participant data for " + ddpParticipantId, results.resultException);
        }
        else {
            return (String) results.resultValue;
        }
    }
}
