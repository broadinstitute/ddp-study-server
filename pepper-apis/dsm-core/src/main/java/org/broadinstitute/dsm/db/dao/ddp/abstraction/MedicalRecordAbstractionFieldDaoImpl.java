package org.broadinstitute.dsm.db.dao.ddp.abstraction;

import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.lddp.db.SimpleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

public class MedicalRecordAbstractionFieldDaoImpl implements MedicalRecordAbstractionFieldDao<MedicalRecordAbstractionFieldDto> {

    private static final Logger logger = LoggerFactory.getLogger(MedicalRecordAbstractionFieldDaoImpl.class);

    private static final String SELECT_ALL_MEDICAL_RECORD_ABSTRACTION_FIELDS = "SELECT * FROM medical_record_abstraction_field ";
    private static final String FILTER_BY_INSTANCE_ID = "WHERE ddp_instance_id = (select ddp_instance_id from ddp_instance where instance_name = ?) ";
    private static final String SELECT_ALL_MEDICAL_RECORD_ABSTRACTION_FIELDS_FILTERED_BY_INSTANCE_ID = SELECT_ALL_MEDICAL_RECORD_ABSTRACTION_FIELDS + FILTER_BY_INSTANCE_ID;

    private static MedicalRecordAbstractionFieldDaoImpl medicalRecordAbstractionFieldDao;


    // for test cases
    protected MedicalRecordAbstractionFieldDaoImpl() {
    }

    public static MedicalRecordAbstractionFieldDaoImpl make() {
        if (medicalRecordAbstractionFieldDao == null) {
            medicalRecordAbstractionFieldDao = new MedicalRecordAbstractionFieldDaoImpl();
        }
        return medicalRecordAbstractionFieldDao;
    }

    public static void setInstance(MedicalRecordAbstractionFieldDaoImpl medicalRecordAbstractionFieldDao) {
        MedicalRecordAbstractionFieldDaoImpl.medicalRecordAbstractionFieldDao = medicalRecordAbstractionFieldDao;
    }


    @Override
    public int create(MedicalRecordAbstractionFieldDto medicalRecordAbstractionFieldDto) {
        return 0;
    }

    @Override
    public int delete(int id) {
        return 0;
    }

    @Override
    public Optional<MedicalRecordAbstractionFieldDto> get(long id) {
        return Optional.empty();
    }

    @Override
    public List<MedicalRecordAbstractionFieldDto> getMedicalRecordAbstractionFieldsByInstanceName(String instanceName) {
        var records = new ArrayList<MedicalRecordAbstractionFieldDto>();
        SimpleResult result = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SELECT_ALL_MEDICAL_RECORD_ABSTRACTION_FIELDS_FILTERED_BY_INSTANCE_ID)) {
                stmt.setString(1, instanceName);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next())
                        records.add(getMedicalRecordAbstractionFieldDtoFromResultSet(rs));
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });
        if (result.resultException != null) {
            throw new RuntimeException("Couldn't get list of medicalRecordAbstractionFields ", result.resultException);
        }
        logger.info("Got " + records.size() + " medicalRecordAbstractionFields in DSM DB");
        return records;
    }

    private MedicalRecordAbstractionFieldDto getMedicalRecordAbstractionFieldDtoFromResultSet(ResultSet rs) throws SQLException {

        var medicalRecordAbstractionFieldId = rs.getLong(DBConstants.MEDICAL_RECORD_ABSTRACTION_FIELD_ID);
        var displayName = rs.getString(DBConstants.DISPLAY_NAME);
        var type = rs.getString(DBConstants.TYPE);
        var additionalType = rs.getString(DBConstants.ADDITIONAL_TYPE);
        var possibleValues = rs.getString(DBConstants.POSSIBLE_VALUE);
        var helpText = rs.getString(DBConstants.HELP_TEXT);
        var fileInfo = rs.getBoolean(DBConstants.FILE_INFO);
        var medicalRecordAbstractionGroupId = rs.getLong(DBConstants.MEDICAL_RECORD_ABSTRACTION_GROUP_ID);
        var ddpInstanceId = rs.getString(DBConstants.DDP_INSTANCE_ID);
        var orderNumber = rs.getInt(DBConstants.ORDER_NUMBER);
        var deleted = rs.getInt(DBConstants.DELETED);

        return new MedicalRecordAbstractionFieldDto(
                medicalRecordAbstractionFieldId, displayName, type,
                additionalType, possibleValues, helpText, fileInfo,
                medicalRecordAbstractionGroupId, ddpInstanceId,
                orderNumber, deleted);
    }
}
