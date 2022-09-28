package org.broadinstitute.dsm.db.dao.abstraction;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.broadinstitute.dsm.db.MedicalRecordFinalDto;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.lddp.db.SimpleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MedicalRecordFinalDaoLive implements MedicalRecordFinalDao {

    private static final Logger logger = LoggerFactory.getLogger(MedicalRecordFinalDaoLive.class);


    private static final String SQL_GET_ALL_BY_INSTANCE_NAME = "SELECT "
            + "p.ddp_participant_id, m.medical_record_final_id, m.participant_id, "
            + "m.medical_record_abstraction_field_id, mf.type, mf.display_name, mf.order_number, m.value, m.no_data, m.data_release_id "
            + "FROM ddp_medical_record_final m "
            + "LEFT JOIN ddp_participant p on m.participant_id = p.participant_id "
            + "LEFT JOIN medical_record_abstraction_field mf ON "
            + "mf.medical_record_abstraction_field_id = m.medical_record_abstraction_field_id "
            + "WHERE p.ddp_instance_id = ((SELECT ddp_instance_id FROM ddp_instance where instance_name = ?));";



    @Override
    public int create(MedicalRecordFinalDto medicalRecordFinalDto) {
        return 0;
    }

    @Override
    public int delete(int id) {
        return 0;
    }

    @Override
    public Optional<MedicalRecordFinalDto> get(long id) {
        return Optional.empty();
    }

    @Override
    public Map<String, List<MedicalRecordFinalDto>> readAllByInstanceName(String instanceName) {
        logger.info("Attempting to read all records from `ddp_medical_record_final` table");
        Map<String, List<MedicalRecordFinalDto>> recordsByGuid = new HashMap<>();
        SimpleResult result = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_GET_ALL_BY_INSTANCE_NAME)) {
                stmt.setString(1, instanceName);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        MedicalRecordFinalDto medicalRecordFinalDto = buildFromResultSet(rs);
                        String guid = rs.getString(DBConstants.DDP_PARTICIPANT_ID);
                        recordsByGuid.computeIfAbsent(guid, key -> new ArrayList<>())
                                .add(medicalRecordFinalDto);
                    }
                } catch (Exception e) {
                    logger.error("Error occurred while reading abstraction final records " + e.getMessage());
                    dbVals.resultException = e;
                }
            } catch (SQLException se) {
                logger.error("Error occurred while reading abstraction final records " + se.getMessage());
                dbVals.resultException = se;
            }
            return dbVals;
        });
        logger.info("Got " + recordsByGuid.size() + " participants final abstraction records");
        return recordsByGuid;
    }

    private MedicalRecordFinalDto buildFromResultSet(ResultSet rs) throws SQLException {
        return new MedicalRecordFinalDto(
                rs.getLong(DBConstants.MEDICAL_RECORD_FINAL_ID),
                rs.getLong(DBConstants.PARTICIPANT_ID),
                rs.getLong(DBConstants.MEDICAL_RECORD_ABSTRACTION_FIELD_ID),
                rs.getString(DBConstants.TYPE),
                rs.getString(DBConstants.VALUE),
                rs.getLong(DBConstants.NO_DATA),
                rs.getLong(DBConstants.DATA_RELEASE_ID),
                rs.getString(DBConstants.DISPLAY_NAME),
                rs.getInt(DBConstants.ORDER_NUMBER));
    }
}
