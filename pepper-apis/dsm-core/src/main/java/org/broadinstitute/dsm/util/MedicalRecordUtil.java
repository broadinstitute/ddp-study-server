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
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.elastic.export.Exportable;
import org.broadinstitute.dsm.model.elastic.export.painless.PutToNestedScriptBuilder;
import org.broadinstitute.dsm.model.elastic.export.painless.UpsertPainlessFacade;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.lddp.db.SimpleResult;
import org.broadinstitute.lddp.handlers.util.Institution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MedicalRecordUtil {

    private static final Logger logger = LoggerFactory.getLogger(MedicalRecordUtil.class);

    public static final String NOT_SPECIFIED = "NOT_SPECIFIED";
    private static final String SQL_UPDATE_PARTICIPANT =
            "UPDATE ddp_participant SET last_version = ?, last_version_date = ?, last_changed = ?, changed_by = ? "
                    + "WHERE ddp_participant_id = ? AND ddp_instance_id = ? AND last_version != ?";
    private static final String SQL_INSERT_INSTITUTION_WITH_DDP_PARTICIPANT_ID =
            "INSERT INTO ddp_institution (ddp_institution_id, type, participant_id, last_changed) VALUES (?, ?, (SELECT participant_id "
                    + "FROM ddp_participant WHERE ddp_participant_id = ? and ddp_instance_id = ?), ?) ON DUPLICATE "
                    + "KEY UPDATE last_changed = ?";
    private static final String SQL_INSERT_INSTITUTION =
            "INSERT INTO ddp_institution (ddp_institution_id, type, participant_id, last_changed) VALUES (?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE last_changed = ?";
    private static final String SQL_INSERT_MEDICAL_RECORD =
            "INSERT INTO ddp_medical_record SET institution_id = ?, last_changed = ?, changed_by = ?";
    private static final String SQL_SELECT_PARTICIPANT_EXISTS = "SELECT count(ddp_participant_id) as participantCount FROM ddp_participant "
            + "WHERE ddp_participant_id = ? AND ddp_instance_id = ?";
    private static final String SQL_SELECT_PARTICIPANT = "SELECT participant_id FROM ddp_participant p "
            + "LEFT JOIN ddp_instance realm on (p.ddp_instance_id = realm.ddp_instance_id) "
            + "WHERE ddp_participant_id = ? AND instance_name = ?";
    private static final String SQL_SELECT_MEDICAL_RECORD_ID_AND_TYPE_FOR_PARTICIPANT =
            "SELECT rec.medical_record_id, inst.type FROM ddp_institution inst, ddp_participant part, ddp_medical_record rec "
                    + "WHERE part.participant_id = inst.participant_id AND rec.institution_id = inst.institution_id "
                    + "AND NOT rec.deleted <=> 1 AND part.participant_id = ? AND inst.type = ?";

    public static int insertMedicalRecord(Connection conn, String institutionId, String ddpParticipantId,
                                          String instanceName, Institution institution, boolean updateElastic) {
        int mrId = writeMedicalRecord(conn, institutionId);
        // don't write medical records that are just to enable onc history. See PEPPER-1370.
        if (updateElastic && !institution.getType().equals(NOT_SPECIFIED)) {
            DDPInstanceDto ddpInstanceDto = new DDPInstanceDao().getDDPInstanceByInstanceName(instanceName).orElseThrow();
            String participantGuid = Exportable.getParticipantGuid(ddpParticipantId, ddpInstanceDto.getEsParticipantIndex());
            if (StringUtils.isBlank(participantGuid)) {
                throw new DsmInternalError("No participant GUID found for participant ID: " + ddpParticipantId);
            }
            MedicalRecord medicalRecord = new MedicalRecord();
            medicalRecord.setMedicalRecordId(mrId);
            medicalRecord.setDdpParticipantId(ddpParticipantId);
            medicalRecord.setInstitutionId(Integer.parseInt(institutionId));
            medicalRecord.setDdpInstanceId(ddpInstanceDto.getDdpInstanceId());
            medicalRecord.setDdpInstitutionId(institution.getId());
            medicalRecord.setType(institution.getType());

            UpsertPainlessFacade.of(DBConstants.DDP_MEDICAL_RECORD_ALIAS, medicalRecord, ddpInstanceDto,
                    ESObjectConstants.MEDICAL_RECORDS_ID, ESObjectConstants.DOC_ID, participantGuid,
                    new PutToNestedScriptBuilder()).export();
        }
        return mrId;
    }

    /**
     * Create a new medical record
     *
     * @return new record ID
     */
    public static int writeMedicalRecord(Connection conn, String institutionId) {
        if (conn == null) {
            throw new DsmInternalError("DB connection was null");
        }
        try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_MEDICAL_RECORD, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, institutionId);
            stmt.setLong(2, System.currentTimeMillis());
            stmt.setString(3, SystemUtil.SYSTEM);
            int result = stmt.executeUpdate();
            if (result != 1) {
                throw new DsmInternalError("Error inserting medical record: number of rows inserted was " + result);
            }
            ResultSet rs = stmt.getGeneratedKeys();
            if (!rs.next()) {
                throw new DsmInternalError("Error getting ID for new medical record");
            }
            logger.info("Added new medical record for institution with id {}", institutionId);
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new DsmInternalError("Error inserting medical record for institution " + institutionId, e);
        }
    }

    public static void writeNewRecordIntoDb(Connection conn, String query, String ddpParticipantId, int instanceId) {
        if (conn != null) {
            long currentMilli = System.currentTimeMillis();
            try (PreparedStatement insertNewRecord = conn.prepareStatement(query)) {
                insertNewRecord.setString(1, ddpParticipantId);
                insertNewRecord.setInt(2, instanceId);
                insertNewRecord.setLong(3, currentMilli);
                insertNewRecord.setString(4, SystemUtil.SYSTEM);
                insertNewRecord.setLong(5, currentMilli);
                insertNewRecord.setString(6, SystemUtil.SYSTEM);
                int result = insertNewRecord.executeUpdate();
                // 1 (inserted) or 2 (updated) is good
                if (result == 2) {
                    logger.info("Updated record for participant {} and instance {}", ddpParticipantId, instanceId);
                } else if (result == 1) {
                    logger.info("Inserted record for participant {} and instance {}", ddpParticipantId, instanceId);
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

    /**
     * Create a new institution and medical record
     *
     * @return medical record ID
     */
    public static int writeInstitution(int participantId, String ddpParticipantId, String institutionType,
                                       String instanceName, boolean updateElastic) {
        long currentMilli = System.currentTimeMillis();
        String ddpInstitutionId = java.util.UUID.randomUUID().toString();
        return inTransaction(conn -> {
            try (PreparedStatement insertInstitution = conn.prepareStatement(SQL_INSERT_INSTITUTION,
                    Statement.RETURN_GENERATED_KEYS)) {
                insertInstitution.setString(1, ddpInstitutionId);
                insertInstitution.setString(2, institutionType);
                insertInstitution.setInt(3, participantId);
                insertInstitution.setLong(4, currentMilli);
                insertInstitution.setLong(5, currentMilli);
                int result = insertInstitution.executeUpdate();
                if (result == 1) {
                    logger.info("Inserted new institution for participant {}", participantId);
                    return createInstitutionMedicalRecord(conn, insertInstitution, ddpParticipantId, instanceName,
                            new Institution(ddpInstitutionId, institutionType), updateElastic);
                } else {
                    throw new DsmInternalError(String.format("Error inserting new institution for participant %s: "
                            + "wrong number of rows inserted %s", ddpParticipantId, result));
                }
            } catch (SQLException e) {
                throw new DsmInternalError("Error inserting new institution for participant " + ddpParticipantId, e);
            }
        });
    }

    /**
     * Returns created medical record ID
     */
    public static Integer writeInstitution(@NonNull Connection conn, @NonNull String ddpParticipantId, int instanceId,
                                           Institution institution, String instanceName) {
        Integer medicalRecordId = null;
        long currentMilli = System.currentTimeMillis();
        String ddpInstitutionId = institution.getId();
        try (PreparedStatement insertInstitution = conn.prepareStatement(SQL_INSERT_INSTITUTION_WITH_DDP_PARTICIPANT_ID,
                Statement.RETURN_GENERATED_KEYS)) {
            insertInstitution.setString(1, ddpInstitutionId);
            insertInstitution.setString(2, institution.getType());
            insertInstitution.setString(3, ddpParticipantId);
            insertInstitution.setInt(4, instanceId);
            insertInstitution.setLong(5, currentMilli);
            insertInstitution.setLong(6, currentMilli);
            int result = insertInstitution.executeUpdate();
            // 1 (inserted) or 2 (updated) is good
            if (result == 2) {
                logger.info("Updated institution with id {}", ddpInstitutionId);
            } else if (result == 1) {
                logger.info("Inserted new institution for participant {}", ddpParticipantId);
                medicalRecordId = createInstitutionMedicalRecord(conn, insertInstitution, ddpParticipantId, instanceName,
                        institution, true);
            } else {
                throw new DsmInternalError("Error updating row");
            }
        } catch (SQLException e) {
            throw new DsmInternalError("Error inserting new institution ", e);
        }
        return medicalRecordId;
    }

    private static int createInstitutionMedicalRecord(Connection conn, PreparedStatement insertInstitution,
                                                      String ddpParticipantId, String instanceName,
                                                      Institution institution, boolean updateElastic) {
        String errorMsg = "Error getting new institution id for participant " + ddpParticipantId;
        try (ResultSet rs = insertInstitution.getGeneratedKeys()) {
            if (rs.next()) {
                String institutionId = rs.getString(1);
                if (StringUtils.isNotBlank(institutionId)) {
                    logger.info("Added institution with id {} for participant {}", institutionId, ddpParticipantId);
                    return MedicalRecordUtil.insertMedicalRecord(conn, institutionId, ddpParticipantId,
                            instanceName, institution, updateElastic);
                }
            }
            throw new DsmInternalError(errorMsg);
        } catch (SQLException e) {
            throw new DsmInternalError(errorMsg, e);
        }
    }

    public static boolean isParticipantInDB(@NonNull Connection conn, @NonNull String participantId, int instanceId) {
        try (PreparedStatement checkParticipant = conn.prepareStatement(SQL_SELECT_PARTICIPANT_EXISTS)) {
            checkParticipant.setString(1, participantId);
            checkParticipant.setInt(2, instanceId);
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

    public static boolean isParticipantInDB(@NonNull String participantId, int instanceId) {
        return inTransaction((conn) -> isParticipantInDB(conn, participantId, instanceId));
    }


    public static boolean updateParticipant(@NonNull Connection conn, @NonNull String ddpParticipantId, int instanceId,
                                            long lastVersion, @NonNull String lastUpdated, @NonNull String userId) {
        try (PreparedStatement updateParticipant = conn.prepareStatement(SQL_UPDATE_PARTICIPANT)) {
            updateParticipant.setLong(1, lastVersion);
            updateParticipant.setString(2, lastUpdated);
            updateParticipant.setLong(3, System.currentTimeMillis());
            updateParticipant.setString(4, userId);
            updateParticipant.setString(5, ddpParticipantId);
            updateParticipant.setInt(6, instanceId);
            updateParticipant.setLong(7, lastVersion);
            if (updateParticipant.executeUpdate() == 1) {
                logger.info("Participant already existed; Updated participant with id {}", ddpParticipantId);
                return true;
            }
        } catch (SQLException e) {
            throw new DsmInternalError("Error updating participant ", e);
        }
        return false;
    }

    public static Integer isInstitutionTypeInDB(@NonNull String participantId) {
        Integer institutionId = isInstitutionTypeInDB(participantId, NOT_SPECIFIED);
        if (institutionId != null) {
            logger.info("Institution of type {} already exists for participant {}", NOT_SPECIFIED, participantId);
        }
        return institutionId;
    }

    public static Integer isInstitutionTypeInDB(@NonNull String participantId, @NonNull String type) {
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
            throw new DsmInternalError("Error getting medical record id for pt w/ id " + participantId, results.resultException);
        }
        return (Integer) results.resultValue;
    }

    public static Integer getParticipantIdByDdpParticipantId(@NonNull String ddpParticipantId, @NonNull String realm) {
        return inTransaction(conn -> {
            Integer participantId = null;
            try (PreparedStatement checkParticipant = conn.prepareStatement(SQL_SELECT_PARTICIPANT)) {
                checkParticipant.setString(1, ddpParticipantId);
                checkParticipant.setString(2, realm);
                try (ResultSet rs = checkParticipant.executeQuery()) {
                    if (rs.next()) {
                        participantId = rs.getInt(DBConstants.PARTICIPANT_ID);
                    }
                }
                return participantId;
            } catch (SQLException e) {
                throw new DsmInternalError("Error getting participant id for ddpParticipantId " + ddpParticipantId, e);
            }
        });
    }
}
