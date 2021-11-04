package org.broadinstitute.dsm.util;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.statics.DBConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

public class MedicalRecordUtil {

    private static final Logger logger = LoggerFactory.getLogger(MedicalRecordUtil.class);

    private static final String SQL_UPDATE_PARTICIPANT = "UPDATE ddp_participant SET last_version = ?, last_version_date = ?, last_changed = ?, changed_by = ? WHERE ddp_participant_id = ? " +
            "AND ddp_instance_id = ? AND last_version != ?";
    private static final String SQL_INSERT_INSTITUTION = "INSERT INTO ddp_institution (ddp_institution_id, type, participant_id, last_changed) VALUES (?, ?, (SELECT participant_id " +
            "FROM ddp_participant WHERE ddp_participant_id = ? and ddp_instance_id = ?), ?) ON DUPLICATE KEY UPDATE last_changed = ?";
    private static final String SQL_INSERT_INSTITUTION_BY_PARTICIPANT = "INSERT INTO ddp_institution (ddp_institution_id, type, participant_id, last_changed) values (?, ?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE last_changed = ?";
    private static final String SQL_INSERT_MEDICAL_RECORD = "INSERT INTO ddp_medical_record SET institution_id = ?, last_changed = ?, changed_by = ?";
    private static final String SQL_SELECT_PARTICIPANT_EXISTS = "SELECT count(ddp_participant_id) as participantCount FROM ddp_participant WHERE ddp_participant_id = ? AND ddp_instance_id = ?";
    private static final String SQL_SELECT_PARTICIPANT_LAST_VERSION = "SELECT last_version FROM ddp_participant WHERE ddp_participant_id = ? AND ddp_instance_id = ?";
    private static final String SQL_SELECT_MEDICAL_RECORD_ID_FOR_PARTICIPANT = "SELECT rec.medical_record_id FROM ddp_institution inst, ddp_participant part, ddp_medical_record rec " +
            "WHERE part.participant_id = inst.participant_id AND rec.institution_id = inst.institution_id AND NOT rec.deleted <=> 1 AND part.ddp_participant_id = ? AND inst.ddp_institution_id = ? AND part.ddp_instance_id = ? AND inst.type = ?";
    private static final String SQL_SELECT_MEDICAL_RECORD_ID_AND_TYPE_FOR_PARTICIPANT = "SELECT rec.medical_record_id, inst.type FROM ddp_institution inst, ddp_participant part, ddp_medical_record rec " +
            "WHERE part.participant_id = inst.participant_id AND rec.institution_id = inst.institution_id AND NOT rec.deleted <=> 1 AND part.participant_id = ? AND inst.type = ?";

    public static final String SYSTEM = "SYSTEM";
    public static final String NOT_SPECIFIED = "NOT_SPECIFIED";
    public static final String OTHER = "OTHER";

    public static void writeNewMedicalRecordIntoDb(Connection conn, String query, String id) {
        if (conn != null) {
            try (PreparedStatement insertNewRecord = conn.prepareStatement(query)) {
                insertNewRecord.setString(1, id);
                insertNewRecord.setLong(2, System.currentTimeMillis());
                insertNewRecord.setString(3, SYSTEM);
                int result = insertNewRecord.executeUpdate();
                if (result > 1) { // 0 or 1 is good
                    throw new RuntimeException("Error updating row");
                }
                logger.info("Added new medical record for institution w/ id " + id);
            }
            catch (SQLException e) {
                throw new RuntimeException("Error inserting new medical record ", e);
            }
        }
        else {
            throw new RuntimeException("DB connection was null");
        }
    }

    public static void writeNewRecordIntoDb(Connection conn, String query, String id, String instanceId) {
        if (conn != null) {
            long currentMilli = System.currentTimeMillis();
            try (PreparedStatement insertNewRecord = conn.prepareStatement(query)) {
                insertNewRecord.setString(1, id);
                insertNewRecord.setString(2, instanceId);
                insertNewRecord.setLong(3, currentMilli);
                insertNewRecord.setString(4, SYSTEM);
                insertNewRecord.setLong(5, currentMilli);
                insertNewRecord.setString(6, SYSTEM);
                int result = insertNewRecord.executeUpdate();
                // 1 (inserted) or 2 (updated) is good
                if (result == 2) {
                    logger.info("Updated record for participant w/ id " + id);
                }
                else if (result == 1) {
                    logger.info("Inserted new record for participant w/ id " + id);
                }
                else {
                    throw new RuntimeException("Error updating row");
                }
            }
            catch (SQLException e) {
                throw new RuntimeException("Error inserting new record ", e);
            }
        }
        else {
            throw new RuntimeException("DB connection was null");
        }
    }

    public static Number isInstitutionInDB(@NonNull Connection conn, @NonNull String participantId, @NonNull String institutionId,
                                           @NonNull String instanceId, @NonNull String type) {
        try (PreparedStatement checkParticipant = conn.prepareStatement(SQL_SELECT_MEDICAL_RECORD_ID_FOR_PARTICIPANT)) {
            checkParticipant.setString(1, participantId);
            checkParticipant.setString(2, institutionId);
            checkParticipant.setString(3, instanceId);
            checkParticipant.setString(4, type);
            try (ResultSet rs = checkParticipant.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(DBConstants.MEDICAL_RECORD_ID);
                }
            }
        }
        catch (SQLException e) {
            throw new RuntimeException("Error updating/inserting participant ", e);
        }
        return null;
    }

    public static void writeInstitutionIntoDb(@NonNull String participantId, @NonNull String type) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement insertInstitution = conn.prepareStatement(SQL_INSERT_INSTITUTION_BY_PARTICIPANT, Statement.RETURN_GENERATED_KEYS)) {
                insertInstitution.setString(1, java.util.UUID.randomUUID().toString());
                insertInstitution.setString(2, type);
                insertInstitution.setString(3, participantId);
                insertInstitution.setLong(4, System.currentTimeMillis());
                insertInstitution.setLong(5, System.currentTimeMillis());
                int result = insertInstitution.executeUpdate();
                // 1 (inserted) or 2 (updated) is good
                if (result == 2) {
                    logger.info("Updated institution for participant w/ id " + participantId);
                }
                else if (result == 1) {
                    logger.info("Inserted new institution for participant w/ id " + participantId);
                    insertInstitution(conn, insertInstitution, participantId, false);
                }
                else {
                    throw new RuntimeException("Error updating row");
                }
            }
            catch (SQLException e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });
        if (results.resultException != null) {
            throw new RuntimeException("Error inserting new institution", results.resultException);
        }
    }

    public static void writeInstitutionIntoDb(@NonNull Connection conn, @NonNull String ddpParticipantId, @NonNull String instanceId,
                                              @NonNull String ddpInstitutionId, @NonNull String type) {
        writeInstitutionIntoDb(conn, ddpParticipantId, instanceId, ddpInstitutionId, type, false);
    }

    public static void writeInstitutionIntoDb(@NonNull Connection conn, @NonNull String ddpParticipantId, @NonNull String instanceId,
                                              @NonNull String ddpInstitutionId, @NonNull String type, boolean setDuplicateFlag) {
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
                }
                else if (result == 1) {
                    logger.info("Inserted new institution for participant w/ id " + ddpParticipantId);
                    insertInstitution(conn, insertInstitution, ddpParticipantId, setDuplicateFlag);
                }
                else {
                    throw new RuntimeException("Error updating row");
                }
            }
            catch (SQLException e) {
                throw new RuntimeException("Error inserting new institution ", e);
            }
        }
        else {
            throw new RuntimeException("DB connection was null");
        }
    }

    private static void insertInstitution(@NonNull Connection conn, @NonNull PreparedStatement insertInstitution, @NonNull String id, boolean setDuplicateFlag) {
        try (ResultSet rs = insertInstitution.getGeneratedKeys()) {
            if (rs.next()) { //no next if no generated return key -> update of institution timestamp does not return new key
                String institutionId = rs.getString(1);
                if (StringUtils.isNotBlank(institutionId)) {
                    logger.info("Added institution w/ id " + institutionId + " for participant w/ id " + id);
                    String query = SQL_INSERT_MEDICAL_RECORD;
                    //TODO can be removed after mbc migration
                    if (setDuplicateFlag) {
                        query = query + ", duplicate = 1";
                    }
                    MedicalRecordUtil.writeNewMedicalRecordIntoDb(conn, query, institutionId);
                }
            }
        }
        catch (Exception e) {
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
        }
        catch (SQLException e) {
            throw new RuntimeException("Error updating/inserting participant ", e);
        }
        return false;
    }

    public static Number getParticipantLastVersion(@NonNull Connection conn, @NonNull String participantId, @NonNull String instanceId) {
        try (PreparedStatement checkParticipant = conn.prepareStatement(SQL_SELECT_PARTICIPANT_LAST_VERSION)) {
            checkParticipant.setString(1, participantId);
            checkParticipant.setString(2, instanceId);
            try (ResultSet rs = checkParticipant.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(DBConstants.LAST_VERSION);
                }
            }
        }
        catch (SQLException e) {
            throw new RuntimeException("Error updating/inserting participant ", e);
        }
        return null;
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
        }
        catch (SQLException e) {
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
            }
            catch (SQLException ex) {
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
