package org.broadinstitute.dsm.util;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.export.Exportable;
import org.broadinstitute.dsm.model.elastic.export.painless.UpsertPainlessFacade;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.lddp.db.SimpleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MedicalRecordUtil {

    private static final Logger logger = LoggerFactory.getLogger(MedicalRecordUtil.class);

    public static final String NOT_SPECIFIED = "NOT_SPECIFIED";
    private static final String SQL_UPDATE_PARTICIPANT =
            "UPDATE ddp_participant SET last_version = ?, last_version_date = ?, last_changed = ?, changed_by = ? "
                    + "WHERE ddp_participant_id = ? AND ddp_instance_id = ? AND last_version != ?";
    private static final String SQL_INSERT_INSTITUTION =
            "INSERT INTO ddp_institution (ddp_institution_id, type, participant_id, last_changed) VALUES (?, ?, (SELECT participant_id "
                    + "FROM ddp_participant WHERE ddp_participant_id = ? and ddp_instance_id = ?), ?) ON DUPLICATE "
                    + "KEY UPDATE last_changed = ?";
    private static final String SQL_INSERT_INSTITUTION_WITH_REALM_NAME =
            "INSERT INTO ddp_institution (ddp_institution_id, type, participant_id, last_changed) VALUES (?, ?, (SELECT participant_id "
                    + "FROM ddp_participant p, ddp_instance realm WHERE realm.ddp_instance_id = p.ddp_instance_id "
                    + "AND p.ddp_participant_id = ? and instance_name = ?), ?) ON DUPLICATE "
                    + "KEY UPDATE last_changed = ?";
    private static final String SQL_INSERT_MEDICAL_RECORD =
            "INSERT INTO ddp_medical_record SET institution_id = ?, last_changed = ?, changed_by = ?";
    private static final String SQL_SELECT_PARTICIPANT_EXISTS = "SELECT count(ddp_participant_id) as participantCount FROM ddp_participant "
            + "WHERE ddp_participant_id = ? AND ddp_instance_id = ?";
    private static final String SQL_SELECT_PARTICIPANT_LAST_VERSION =
            "SELECT last_version FROM ddp_participant WHERE ddp_participant_id = ? AND ddp_instance_id = ?";
    private static final String SQL_SELECT_MEDICAL_RECORD_ID_AND_TYPE_FOR_PARTICIPANT =
            "SELECT rec.medical_record_id, inst.type FROM ddp_institution inst, ddp_participant part, ddp_medical_record rec "
                    + "WHERE part.participant_id = inst.participant_id AND rec.institution_id = inst.institution_id "
                    + "AND NOT rec.deleted <=> 1 AND part.participant_id = ? AND inst.type = ?";

    public static void writeNewMedicalRecordIntoDb(Connection conn, String query, String institutionId, String ddpParticipantId,
                                                   String instanceName, String ddpInstitutionId) {
        Integer mrId = null;
        if (conn != null) {
            try (PreparedStatement insertNewRecord = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
                insertNewRecord.setString(1, institutionId);
                insertNewRecord.setLong(2, System.currentTimeMillis());
                insertNewRecord.setString(3, SystemUtil.SYSTEM);
                int result = insertNewRecord.executeUpdate();
                if (result > 1) { // 0 or 1 is good
                    throw new RuntimeException("Error updating row");
                }
                if (result == 1) {
                    try (ResultSet rs = insertNewRecord.getGeneratedKeys()) {
                        if (rs.next()) { //no next if no generated return key -> update of institution timestamp does not return new key
                            mrId = rs.getInt(1);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Error getting id of new medical record ", e);
                    }
                }
                logger.info("Added new medical record for institution w/ id " + institutionId);
            } catch (SQLException e) {
                throw new RuntimeException("Error inserting new medical record ", e);
            }
        } else {
            throw new RuntimeException("DB connection was null");
        }
        if (mrId != null) {
            DDPInstanceDto ddpInstanceDto = new DDPInstanceDao().getDDPInstanceByInstanceName(instanceName).orElseThrow();
            String participantGuid = Exportable.getParticipantGuid(ddpParticipantId, ddpInstanceDto.getEsParticipantIndex());
            MedicalRecord medicalRecord = new MedicalRecord();
            medicalRecord.setMedicalRecordId(mrId);
            medicalRecord.setDdpParticipantId(ddpParticipantId);
            medicalRecord.setInstitutionId(Long.parseLong(institutionId));
            medicalRecord.setDdpInstanceId(ddpInstanceDto.getDdpInstanceId());
            medicalRecord.setDdpInstitutionId(ddpInstitutionId);

            UpsertPainlessFacade.of(DBConstants.DDP_MEDICAL_RECORD_ALIAS, medicalRecord, ddpInstanceDto,
                    ESObjectConstants.MEDICAL_RECORDS_ID, ESObjectConstants.DOC_ID, participantGuid).export();
        }
    }

    public static void writeNewRecordIntoDb(Connection conn, String query, String id, String instanceId) {
        if (conn != null) {
            long currentMilli = System.currentTimeMillis();
            try (PreparedStatement insertNewRecord = conn.prepareStatement(query)) {
                insertNewRecord.setString(1, id);
                insertNewRecord.setString(2, instanceId);
                insertNewRecord.setLong(3, currentMilli);
                insertNewRecord.setString(4, SystemUtil.SYSTEM);
                insertNewRecord.setLong(5, currentMilli);
                insertNewRecord.setString(6, SystemUtil.SYSTEM);
                int result = insertNewRecord.executeUpdate();
                // 1 (inserted) or 2 (updated) is good
                if (result == 2) {
                    logger.info("Updated record for participant w/ id " + id);
                } else if (result == 1) {
                    logger.info("Inserted new record for participant w/ id " + id);
                } else {
                    throw new RuntimeException("Error updating row");
                }
            } catch (SQLException e) {
                throw new RuntimeException("Error inserting new record ", e);
            }
        } else {
            throw new RuntimeException("DB connection was null");
        }
    }

    public static void writeInstitutionIntoDb(@NonNull String ddpParticipantId, @NonNull String type, String instanceName) {
        long currentMilli = System.currentTimeMillis();
        String ddpInstitutionId = java.util.UUID.randomUUID().toString();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement insertInstitution = conn.prepareStatement(SQL_INSERT_INSTITUTION_WITH_REALM_NAME,
                    Statement.RETURN_GENERATED_KEYS)) {
                insertInstitution.setString(1, ddpInstitutionId);
                insertInstitution.setString(2, type);
                insertInstitution.setString(3, ddpParticipantId);
                insertInstitution.setString(4, instanceName);
                insertInstitution.setLong(5, currentMilli);
                insertInstitution.setLong(6, currentMilli);
                int result = insertInstitution.executeUpdate();
                // 1 (inserted) or 2 (updated) is good
                if (result == 2) {
                    logger.info("Updated institution for participant w/ id " + ddpParticipantId);
                } else if (result == 1) {
                    logger.info("Inserted new institution for participant w/ id " + ddpParticipantId);
                    insertInstitution(conn, insertInstitution, ddpParticipantId, instanceName, ddpInstitutionId);
                } else {
                    throw new RuntimeException("Error updating row");
                }
            } catch (SQLException e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });
        if (results.resultException != null) {
            throw new RuntimeException("Error inserting new institution", results.resultException);
        }
    }

    public static void writeInstitutionIntoDb(@NonNull Connection conn, @NonNull String ddpParticipantId, @NonNull String instanceId,
                                              @NonNull String ddpInstitutionId, @NonNull String type, String instanceName) {
        if (conn != null) {
            long currentMilli = System.currentTimeMillis();
            try (PreparedStatement insertInstitution = conn.prepareStatement(SQL_INSERT_INSTITUTION, Statement.RETURN_GENERATED_KEYS)) {
                insertInstitution.setString(1, ddpInstitutionId);
                insertInstitution.setString(2, type);
                insertInstitution.setString(3, ddpParticipantId);
                insertInstitution.setString(4, instanceId);
                insertInstitution.setLong(5, currentMilli);
                insertInstitution.setLong(6, currentMilli);
                int result = insertInstitution.executeUpdate();
                // 1 (inserted) or 2 (updated) is good
                if (result == 2) {
                    logger.info("Updated institution w/ id " + ddpInstitutionId);
                } else if (result == 1) {
                    logger.info("Inserted new institution for participant w/ id " + ddpParticipantId);
                    insertInstitution(conn, insertInstitution, ddpParticipantId, instanceName, ddpInstitutionId);
                } else {
                    throw new RuntimeException("Error updating row");
                }
            } catch (SQLException e) {
                throw new RuntimeException("Error inserting new institution ", e);
            }
        } else {
            throw new RuntimeException("DB connection was null");
        }
    }

    private static void insertInstitution(@NonNull Connection conn, @NonNull PreparedStatement insertInstitution,
                                          @NonNull String ddpParticipantId, String instanceName, String ddpInstitutionId) {
        try (ResultSet rs = insertInstitution.getGeneratedKeys()) {
            if (rs.next()) { //no next if no generated return key -> update of institution timestamp does not return new key
                String institutionId = rs.getString(1);
                if (StringUtils.isNotBlank(institutionId)) {
                    logger.info("Added institution w/ id " + institutionId + " for participant w/ id " + ddpParticipantId);
                    MedicalRecordUtil.writeNewMedicalRecordIntoDb(conn, SQL_INSERT_MEDICAL_RECORD, institutionId, ddpParticipantId,
                            instanceName, ddpInstitutionId);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error getting id of new institution ", e);
        }
    }

    public static boolean isParticipantInDB(@NonNull Connection conn, @NonNull String participantId, @NonNull String instanceId) {
        try (PreparedStatement checkParticipant = conn.prepareStatement(SQL_SELECT_PARTICIPANT_EXISTS)) {
            checkParticipant.setString(1, participantId);
            checkParticipant.setString(2, instanceId);
            try (ResultSet rs = checkParticipant.executeQuery()) {
                if (rs.next()) {
                    if (rs.getInt(DBConstants.PARTICIPANT_COUNT) == 1) {
                        return true;
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error updating/inserting participant ", e);
        }
        return false;
    }

    public static boolean updateParticipant(@NonNull Connection conn, @NonNull String participantId, @NonNull String instanceId,
                                            @NonNull long lastVersion, @NonNull String lastUpdated, @NonNull String userId) {
        try (PreparedStatement updateParticipant = conn.prepareStatement(SQL_UPDATE_PARTICIPANT)) {
            updateParticipant.setLong(1, lastVersion);
            updateParticipant.setString(2, lastUpdated);
            updateParticipant.setLong(3, System.currentTimeMillis());
            updateParticipant.setString(4, userId);
            updateParticipant.setString(5, participantId);
            updateParticipant.setString(6, instanceId);
            updateParticipant.setLong(7, lastVersion);
            if (updateParticipant.executeUpdate() == 1) {
                logger.info("Participant already existed; Updated participant w/ id " + participantId);
                return true;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error updating participant ", e);
        }
        return false;
    }

    public static Number isInstitutionTypeInDB(@NonNull String participantId) {
        return isInstitutionTypeInDB(participantId, NOT_SPECIFIED);
    }

    public static Number isInstitutionTypeInDB(@NonNull String participantId, @NonNull String type) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement checkParticipant = conn.prepareStatement(SQL_SELECT_MEDICAL_RECORD_ID_AND_TYPE_FOR_PARTICIPANT)) {
                checkParticipant.setString(1, participantId);
                checkParticipant.setString(2, type);
                try (ResultSet rs = checkParticipant.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = rs.getInt(DBConstants.MEDICAL_RECORD_ID);
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting medical record id for pt w/ id " + participantId, results.resultException);
        }
        return (Number) results.resultValue;
    }
}
