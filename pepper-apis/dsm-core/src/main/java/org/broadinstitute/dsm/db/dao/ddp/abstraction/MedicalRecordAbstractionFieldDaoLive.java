
package org.broadinstitute.dsm.db.dao.ddp.abstraction;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.lddp.db.SimpleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MedicalRecordAbstractionFieldDaoLive implements MedicalRecordAbstractionFieldDao<MedicalRecordAbstractionFieldDto> {

    private static final Logger logger = LoggerFactory.getLogger(MedicalRecordAbstractionFieldDaoLive.class);

    private static final String SELECT_ALL_MEDICAL_RECORD_ABSTRACTION_FIELDS = "SELECT * FROM medical_record_abstraction_field ";

    private static final String FILTER_BY_INSTANCE_ID =
            "WHERE ddp_instance_id = (select ddp_instance_id from ddp_instance where instance_name = ?) ";

    private static final String SELECT_ALL_MEDICAL_RECORD_ABSTRACTION_FIELDS_FILTERED_BY_INSTANCE_ID =
            SELECT_ALL_MEDICAL_RECORD_ABSTRACTION_FIELDS + FILTER_BY_INSTANCE_ID;

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
        List<MedicalRecordAbstractionFieldDto> records = new ArrayList<>();
        SimpleResult result = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SELECT_ALL_MEDICAL_RECORD_ABSTRACTION_FIELDS_FILTERED_BY_INSTANCE_ID)) {
                stmt.setString(1, instanceName);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        records.add(getMedicalRecordAbstractionFieldDtoFromResultSet(rs));
                    }
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

        long abstractionFieldId = rs.getLong(DBConstants.MEDICAL_RECORD_ABSTRACTION_FIELD_ID);
        String displayName      = rs.getString(DBConstants.DISPLAY_NAME);
        String type             = rs.getString(DBConstants.TYPE);
        String additionalType   = rs.getString(DBConstants.ADDITIONAL_TYPE);
        String possibleValues   = rs.getString(DBConstants.POSSIBLE_VALUE);
        String helpText         = rs.getString(DBConstants.HELP_TEXT);
        boolean fileInfo        = rs.getBoolean(DBConstants.FILE_INFO);
        long abstractionGroupId = rs.getLong(DBConstants.MEDICAL_RECORD_ABSTRACTION_GROUP_ID);
        String ddpInstanceId    = rs.getString(DBConstants.DDP_INSTANCE_ID);
        int orderNumber         = rs.getInt(DBConstants.ORDER_NUMBER);
        int deleted             = rs.getInt(DBConstants.DELETED);

        return new MedicalRecordAbstractionFieldDto(
                abstractionFieldId, displayName, type,
                additionalType, possibleValues, helpText, fileInfo,
                abstractionGroupId, ddpInstanceId,
                orderNumber, deleted);
    }
}
