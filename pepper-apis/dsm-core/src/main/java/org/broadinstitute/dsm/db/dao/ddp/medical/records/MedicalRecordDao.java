package org.broadinstitute.dsm.db.dao.ddp.medical.records;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Optional;

import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dao.util.DaoUtil;
import org.broadinstitute.dsm.db.dao.util.ResultsBuilder;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.SystemUtil;
import org.broadinstitute.lddp.db.SimpleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class MedicalRecordDao implements Dao<MedicalRecord> {

    private static final Logger logger = LoggerFactory.getLogger(MedicalRecordDao.class);

    public static final String SQL_INSERT_NEW_MEDICAL_RECORD = "INSERT INTO ddp_medical_record (institution_id, name, contact, phone, "
            + "fax, fax_sent, fax_sent_by, fax_confirmed, fax_sent_2, fax_sent_2_by, fax_confirmed_2, fax_sent_3, "
            + "fax_sent_3_by, fax_confirmed_3, "
            + "mr_received, mr_document, mr_problem, mr_problem_text, unable_obtain, unable_obtain_text, "
            + "followup_required, followup_required_text, "
            + "duplicate, international, cr_required, pathology_present, notes, follow_ups, additional_values_json, "
            + "last_changed, changed_by, mr_document_file_names, deleted) "
            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";


    public static final String SQL_SELECT_BY_ID = "SELECT * FROM ddp_medical_record WHERE medical_record_id = ?;";

    private static final String SQL_DELETE_BY_ID = "DELETE FROM ddp_medical_record WHERE medical_record_id = ?";

    public static MedicalRecordDao of() {
        return new MedicalRecordDao();
    }

    @Override
    public int create(MedicalRecord medicalRecord) {
        logger.info(String.format("Attempting to create a new medical_record with institution_id = %s", medicalRecord.getInstitutionId()));
        SimpleResult simpleResult = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult(-1);
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_NEW_MEDICAL_RECORD, Statement.RETURN_GENERATED_KEYS)) {
                setFieldsToStatement(medicalRecord, stmt);
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
            throw new RuntimeException("Error while inserting medical record with institution id: "
                    + medicalRecord.getInstitutionId(), simpleResult.resultException);
        }
        logger.info(String.format("A new medical_record with institution_id = %s has been created successfully",
                medicalRecord.getInstitutionId()));
        return (int) simpleResult.resultValue;
    }

    private void setFieldsToStatement(MedicalRecord medicalRecord, PreparedStatement stmt) throws SQLException {
        stmt.setLong(1, medicalRecord.getInstitutionId());
        stmt.setString(2, medicalRecord.getName());
        stmt.setString(3, medicalRecord.getContact());
        stmt.setString(4, medicalRecord.getPhone());
        stmt.setString(5, medicalRecord.getFax());
        stmt.setString(6, medicalRecord.getFaxSent());
        stmt.setString(7, medicalRecord.getFaxSentBy());
        stmt.setString(8, medicalRecord.getFaxConfirmed());
        stmt.setString(9, medicalRecord.getFaxSent2());
        stmt.setString(10, medicalRecord.getFaxSent2By());
        stmt.setString(11, medicalRecord.getFaxConfirmed2());
        stmt.setString(12, medicalRecord.getFaxSent3());
        stmt.setString(13, medicalRecord.getFaxSent3By());
        stmt.setString(14, medicalRecord.getFaxConfirmed3());
        stmt.setString(15, medicalRecord.getMrReceived());
        stmt.setString(16, medicalRecord.getMrDocument());
        stmt.setBoolean(17, medicalRecord.isMrProblem());
        stmt.setString(18, medicalRecord.getMrProblemText());
        stmt.setBoolean(19, medicalRecord.isUnableObtain());
        stmt.setString(20, medicalRecord.getUnableObtainText());
        stmt.setBoolean(21, medicalRecord.isFollowupRequired());
        stmt.setString(22, medicalRecord.getFollowupRequiredText());
        stmt.setBoolean(23, medicalRecord.isDuplicate());
        stmt.setBoolean(24, medicalRecord.isInternational());
        stmt.setBoolean(25, medicalRecord.isCrRequired());
        stmt.setString(26, medicalRecord.getPathologyPresent());
        stmt.setString(27, medicalRecord.getNotes());
        stmt.setString(28, Arrays.toString(medicalRecord.getFollowUps()));
        stmt.setString(29, medicalRecord.getAdditionalValuesJson());
        stmt.setLong(30, System.currentTimeMillis());
        stmt.setString(31, SystemUtil.SYSTEM);
        stmt.setString(32, medicalRecord.getMrDocumentFileNames());
        stmt.setBoolean(33, medicalRecord.getDeleted() != null);
    }

    @Override
    public int delete(int id) {
        SimpleResult simpleResult = DaoUtil.deleteById(id, SQL_DELETE_BY_ID);
        if (simpleResult.resultException != null) {
            throw new RuntimeException("Error deleting medical record with id: " + id, simpleResult.resultException);
        }
        return (int) simpleResult.resultValue;
    }

    @Override
    public Optional<MedicalRecord> get(long id) {
        MedicalRecordDao.BuildMedicalRecord builder = new MedicalRecordDao.BuildMedicalRecord();
        SimpleResult res = DaoUtil.getById(id, SQL_SELECT_BY_ID, builder);
        if (res.resultException != null) {
            throw new RuntimeException("Error getting medical record with id: " + id,
                    res.resultException);
        }
        return Optional.of((MedicalRecord) res.resultValue);
    }

    private static class BuildMedicalRecord implements ResultsBuilder {

        public Object build(ResultSet rs) throws SQLException {
            return new MedicalRecord(rs.getInt(DBConstants.MEDICAL_RECORD_ID),
                    rs.getInt(DBConstants.INSTITUTION_ID));
        }
    }
}
