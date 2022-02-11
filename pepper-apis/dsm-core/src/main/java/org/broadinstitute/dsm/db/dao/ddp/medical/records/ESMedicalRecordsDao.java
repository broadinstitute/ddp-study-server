package org.broadinstitute.dsm.db.dao.ddp.medical.records;

import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dto.medical.records.ESMedicalRecordsDto;
import org.broadinstitute.dsm.statics.DBConstants;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

public class ESMedicalRecordsDao implements Dao<ESMedicalRecordsDto> {

    public static final String SQL_SELECT_ES_MEDICAL_RECORD =
        "SELECT " +
        "dp.ddp_participant_id, " +
        "mr.medical_record_id, " +
        "mr.name, " +
        "di.type, " +
        "mr.mr_received, " +
        "mr.fax_sent " +
                "FROM " +
        "ddp_medical_record mr " +
        "LEFT JOIN " +
        "ddp_institution di ON mr.institution_id = di.institution_id " +
        "LEFT JOIN " +
        "ddp_participant dp ON di.participant_id = dp.participant_id";

    public static final String BY_INSTANCE_ID = " WHERE dp.ddp_instance_id = ?";

    @Override
    public int create(ESMedicalRecordsDto esMedicalRecordsDto) {
        return 0;
    }

    @Override
    public int delete(int id) {
        return 0;
    }

    @Override
    public Optional<ESMedicalRecordsDto> get(long id) {
        return Optional.empty();
    }

    public List<ESMedicalRecordsDto> getESMedicalRecordsByInstanceId(int instanceId) {
        List<ESMedicalRecordsDto> medicalRecordsDtoListES = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult execResult = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_ES_MEDICAL_RECORD + BY_INSTANCE_ID)) {
                stmt.setInt(1, instanceId);
                try(ResultSet ESmrRs = stmt.executeQuery()) {
                    while (ESmrRs.next()) {
                        medicalRecordsDtoListES.add(
                                new ESMedicalRecordsDto(
                                        ESmrRs.getString(DBConstants.DDP_PARTICIPANT_ID),
                                        ESmrRs.getInt(DBConstants.MEDICAL_RECORD_ID),
                                        ESmrRs.getString(DBConstants.NAME),
                                        ESmrRs.getString(DBConstants.TYPE),
                                        ESmrRs.getString(DBConstants.MR_RECEIVED),
                                        ESmrRs.getString(DBConstants.FAX_SENT)
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
            throw new RuntimeException("Error getting medical records by instanceId " + instanceId, results.resultException);
        }
        return medicalRecordsDtoListES;
    }
}
