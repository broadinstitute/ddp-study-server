package org.broadinstitute.dsm.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.MedicalRecordLog;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.service.medicalrecord.MedicalRecordService;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.lddp.handlers.util.Institution;
import org.broadinstitute.lddp.handlers.util.InstitutionRequest;

@Slf4j
public class DDPMedicalRecordDataRequest {

    public static final String SQL_INSERT_ONC_HISTORY =
            "INSERT INTO ddp_onc_history SET participant_id = (SELECT participant_id FROM ddp_participant "
                    + "WHERE ddp_participant_id = ? and ddp_instance_id = ?), last_changed = ?, changed_by = ? ON DUPLICATE KEY "
                    + "UPDATE last_changed = ?, changed_by = ?";
    public static final String SQL_INSERT_PARTICIPANT_RECORD =
            "INSERT INTO ddp_participant_record SET participant_id = (SELECT participant_id FROM ddp_participant "
                    + "WHERE ddp_participant_id = ? and ddp_instance_id = ?), last_changed = ?, changed_by = ? ON DUPLICATE KEY "
                    + "UPDATE last_changed = ?, changed_by = ?";
    private static final String SQL_INSERT_MEDICAL_RECORD_LOG =
            "INSERT INTO ddp_medical_record_log SET medical_record_id = ?, type = ?, last_changed = ?";
    private static final String SQL_SELECT_MEDICAL_RECORD_LOG =
            "SELECT rec.medical_record_id, log.type, log.date FROM ddp_medical_record rec "
                    + "LEFT JOIN ddp_institution inst on (rec.institution_id = inst.institution_id) "
                    + "LEFT JOIN ddp_participant part on (part.participant_id = inst.participant_id) "
                    + "LEFT JOIN ddp_medical_record_log log on (log.medical_record_id = rec.medical_record_id) "
                    + "WHERE part.ddp_participant_id = ? AND part.ddp_instance_id = ? "
                    + "AND NOT rec.deleted <=> 1 AND rec.fax_sent is not null AND (log.type is null OR log.type = ?)";

    public void requestAndWriteParticipantInstitutions() {
        new MedicalRecordService().requestParticipantInstitutions();

    }

    // TODO: these methods should be moved to MedicalRecordService. They are under test now indirectly via
    //  MedicalRecordTestUtil, and I did a bit of refactoring, but I want to clean them up before moving. -DC

    /**
     * Returns created medical record IDs
     */
    public static List<Integer> writeInstitutionInfo(Connection conn, InstitutionRequest institutionRequest,
                                                     ParticipantDto participantDto, DDPInstance ddpInstance) {
        List<Integer> medicalRecordIds = new ArrayList<>();
        String ddpParticipantId = participantDto.getRequiredDdpParticipantId();
        int instanceId = ddpInstance.getDdpInstanceIdAsInt();

        List<Institution> institutions = institutionRequest.getInstitutions();
        if (!institutions.isEmpty()) {
            log.info("Creating medical record bundle for participant {} with {} institutions for instance {}",
                    ddpParticipantId, institutions.size(), ddpInstance.getName());
            // TODO this should be rewritten to verify the DDP participant ID and get the participant ID to use in the following
            // calls (which should be modified to use the participant ID instead of repeatedly doing SQL queries
            // to get it) -DC
            // TODO: OncHistory is created but not written to ES, but will be written on export. Use
            //  OncHistoryService.createEmptyOncHistory. -DC
            MedicalRecordUtil.writeNewRecordIntoDb(conn, SQL_INSERT_ONC_HISTORY, ddpParticipantId, instanceId);
            MedicalRecordUtil.writeNewRecordIntoDb(conn, SQL_INSERT_PARTICIPANT_RECORD, ddpParticipantId, instanceId);

            for (Institution institution : institutions) {
                medicalRecordIds.add(MedicalRecordUtil.writeInstitution(conn, ddpParticipantId, instanceId,
                        institution, ddpInstance.getName()));
            }
        } else {
            log.info("Institution list was empty for participant {}", ddpParticipantId);
        }
        return medicalRecordIds;
    }

    public static void updateMedicalRecordLog(Connection conn, String ddpParticipantId, int instanceId) {
        Collection<Integer> allMedicalRecordIds = getMedicalRecordIds(conn, ddpParticipantId, instanceId);
        if (!allMedicalRecordIds.isEmpty()) {
            for (Integer medicalRecordId : allMedicalRecordIds) {
                writingMedicalRecordLogIntoDb(conn, medicalRecordId);
            }
        }
    }

    private static void writingMedicalRecordLogIntoDb(Connection conn, Integer medicalRecordId) {
        if (conn != null) {
            try (PreparedStatement insertMedicalRecordLog = conn.prepareStatement(SQL_INSERT_MEDICAL_RECORD_LOG)) {
                insertMedicalRecordLog.setInt(1, medicalRecordId);
                insertMedicalRecordLog.setString(2, MedicalRecordLog.DATA_REVIEW);
                insertMedicalRecordLog.setLong(3, System.currentTimeMillis());
                int result = insertMedicalRecordLog.executeUpdate();
                if (result != 1) {
                    throw new RuntimeException("Error updating row");
                }
                log.info("Added medical record log for medical record w/ id " + medicalRecordId);
            } catch (SQLException e) {
                throw new RuntimeException("Error inserting new medical record ", e);
            }
        } else {
            throw new RuntimeException("DB connection was null");
        }
    }

    public static Collection<Integer> getMedicalRecordIds(Connection conn, String participantId, int instanceId) {
        Collection<Integer> medicalRecordIds = new HashSet<>();
        if (conn != null) {
            try (PreparedStatement getMedicalRecordIds = conn.prepareStatement(SQL_SELECT_MEDICAL_RECORD_LOG)) {
                getMedicalRecordIds.setString(1, participantId);
                getMedicalRecordIds.setInt(2, instanceId);
                getMedicalRecordIds.setString(3, MedicalRecordLog.DATA_REVIEW);
                try (ResultSet rs = getMedicalRecordIds.executeQuery()) {
                    while (rs.next()) {
                        Integer medicalRecordId = rs.getInt(DBConstants.MEDICAL_RECORD_ID);
                        String type = rs.getString(DBConstants.TYPE);
                        if ((StringUtils.isNotBlank(rs.getString(DBConstants.DATE)) && MedicalRecordLog.DATA_REVIEW.equals(type))
                                || StringUtils.isBlank(type)) {
                            medicalRecordIds.add(medicalRecordId);
                        } else {
                            if (medicalRecordIds.contains(medicalRecordId)) {
                                medicalRecordIds.remove(medicalRecordId);
                            }
                        }
                    }
                } catch (SQLException e1) {
                    throw new RuntimeException("Error getting medicalRecordId ", e1);
                }
            } catch (SQLException e) {
                throw new RuntimeException("Error getting medicalRecordIds ", e);
            }
        }
        return medicalRecordIds;
    }
}
