package org.broadinstitute.dsm.util;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.handlers.util.Institution;
import org.broadinstitute.ddp.handlers.util.InstitutionRequest;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.MedicalRecordLog;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.RoutePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

public class DDPMedicalRecordDataRequest {

    private static final Logger logger = LoggerFactory.getLogger(DDPMedicalRecordDataRequest.class);

    public static final String SQL_INSERT_ONC_HISTORY = "INSERT INTO ddp_onc_history SET participant_id = (SELECT participant_id FROM ddp_participant " +
            "WHERE ddp_participant_id = ? and ddp_instance_id = ?), last_changed = ?, changed_by = ? ON DUPLICATE KEY UPDATE last_changed = ?, changed_by = ?";
    public static final String SQL_INSERT_PARTICIPANT_RECORD = "INSERT INTO ddp_participant_record SET participant_id = (SELECT participant_id FROM ddp_participant " +
            "WHERE ddp_participant_id = ? and ddp_instance_id = ?), last_changed = ?, changed_by = ? ON DUPLICATE KEY UPDATE last_changed = ?, changed_by = ?";
    private static final String SQL_INSERT_MEDICAL_RECORD_LOG = "INSERT INTO ddp_medical_record_log SET medical_record_id = ?, type = ?, last_changed = ?";
    private static final String SQL_SELECT_MEDICAL_RECORD_LOG = "SELECT rec.medical_record_id, log.type, log.date FROM ddp_medical_record rec " +
            "LEFT JOIN ddp_institution inst on (rec.institution_id = inst.institution_id) LEFT JOIN ddp_participant part on (part.participant_id = inst.participant_id) " +
            "LEFT JOIN ddp_medical_record_log log on (log.medical_record_id = rec.medical_record_id) WHERE part.ddp_participant_id = ? AND part.ddp_instance_id = ? " +
            "AND NOT rec.deleted <=> 1 AND rec.fax_sent is not null AND (log.type is null OR log.type = ?)";
    private static final String SQL_SELECT_LOG_FOR_MEDICAL_RECORD = "SELECT rec.medical_record_id, log.type, log.date, rec.fax_sent FROM ddp_medical_record rec " +
            "LEFT JOIN ddp_medical_record_log log on (log.medical_record_id = rec.medical_record_id) WHERE rec.medical_record_id = ? AND rec.fax_sent is not null " +
            "AND (log.type is null OR log.type = ?) ORDER BY medical_record_log_id desc";

    private long lastTimeChecked = System.currentTimeMillis();

    /**
     * Requesting 'new' DDPKitRequests and write them into ddp_kit_request
     *
     * @throws Exception
     */
    public void requestAndWriteParticipantInstitutions() {
        requestFromDDPs();
    }

    public void requestFromDDPs() {
        try {
            List<DDPInstance> ddpInstances = DDPInstance.getDDPInstanceListWithRole(DBConstants.HAS_MEDICAL_RECORD_ENDPOINTS);
            if (ddpInstances != null) {
                inTransaction((conn) -> {
                    for (DDPInstance ddpInstance : ddpInstances) {
                        if (ddpInstance.isHasRole()) {
                            Long value = DBUtil.getBookmark(conn, ddpInstance.getDdpInstanceId());
                            if (value != null && value != -1) {
                                String dsmRequest = ddpInstance.getBaseUrl() + RoutePath.DDP_PARTICIPANT_INSTITUTIONS + "/" + value;
                                try {
                                    InstitutionRequest[] institutionRequests = DDPRequestUtil.getResponseObject(InstitutionRequest[].class, dsmRequest, ddpInstance.getName(), ddpInstance.isHasAuth0Token());
                                    if (institutionRequests != null && institutionRequests.length > 0) {
                                        logger.info("Got " + institutionRequests.length + " InstitutionRequests");
                                        for (InstitutionRequest institutionRequest : institutionRequests) {
                                            try {
                                                writeParticipantIntoDb(conn, ddpInstance.getDdpInstanceId(), institutionRequest);
                                                value = Math.max(value, institutionRequest.getId());
                                            }
                                            catch (Exception e) {
                                                logger.error("Failed to insert participant for mr into db ", e);
                                            }
                                        }
                                        DBUtil.updateBookmark(conn, value, ddpInstance.getDdpInstanceId());
                                    }
                                }
                                catch (Exception e) {
                                    logger.error("Couldn't get participants and institutions for ddpInstance " + ddpInstance.getName(), e);
                                }
                            }
                            else {
                                logger.error("Couldn't get maxParticipantId for ddpInstance " + ddpInstance.getName());
                            }
                        }
                    }
                    return null;
                });
            }
        }
        catch (Exception e) {
            throw new RuntimeException("Error getting participant information ", e);
        }
    }

    public void writeParticipantIntoDb(@NonNull Connection conn, @NonNull String instanceId, InstitutionRequest institutionRequest) {
        if (MedicalRecordUtil.isParticipantInDB(conn, institutionRequest.getParticipantId(), instanceId)) {
            //participant already exists
            if (MedicalRecordUtil.updateParticipant(conn, institutionRequest.getParticipantId(), instanceId,
                    institutionRequest.getId(), institutionRequest.getLastUpdated(), MedicalRecordUtil.SYSTEM)) {
                writeInstitutionInfo(conn, institutionRequest, instanceId);
                //participant lastVersion changed
                Collection<Number> medicalRecordIds = getMedicalRecordIds(conn, institutionRequest.getParticipantId(), instanceId);
                if (medicalRecordIds != null && !medicalRecordIds.isEmpty()) {
                    for (Number medicalRecordId : medicalRecordIds) {
                        writingMedicalRecordLogIntoDb(conn, medicalRecordId);
                    }
                }
            }
        }
        else {
            //new participant
            MedicalRecordUtil.writeParticipantIntoDB(conn, institutionRequest.getParticipantId(), instanceId,
                    institutionRequest.getId(), institutionRequest.getLastUpdated(), MedicalRecordUtil.SYSTEM);
            writeInstitutionInfo(conn, institutionRequest, instanceId);
        }
    }

    public void writePhysiciansIntoDb(@NonNull Connection conn, @NonNull String instanceId, @NonNull String participantId, @NonNull String ptLastUpdated,
                                      @NonNull String institutionId, @NonNull boolean institutionChangedSinceLastChecked, @NonNull String type, boolean setDuplicateFlag) {
        if (!MedicalRecordUtil.isParticipantInDB(conn, participantId, instanceId)) {
            //new participant
            MedicalRecordUtil.writeParticipantIntoDB(conn, participantId, instanceId,
                    0, ptLastUpdated, MedicalRecordUtil.SYSTEM);
            MedicalRecordUtil.writeNewRecordIntoDb(conn, SQL_INSERT_ONC_HISTORY, participantId, instanceId);
            MedicalRecordUtil.writeNewRecordIntoDb(conn, SQL_INSERT_PARTICIPANT_RECORD, participantId, instanceId);
        }
        else {
            //pt already exists
            Number lastVersion = MedicalRecordUtil.getParticipantLastVersion(conn, participantId, instanceId);
            long newVersion = lastVersion.longValue() + 1;
            MedicalRecordUtil.updateParticipant(conn, participantId, instanceId, newVersion, ptLastUpdated, MedicalRecordUtil.SYSTEM);
        }
        //new physician/institution
        Number medicalRecordId = MedicalRecordUtil.isInstitutionInDB(conn, participantId, institutionId, instanceId, type);
        if (medicalRecordId == null) {
            MedicalRecordUtil.writeInstitutionIntoDb(conn, participantId, instanceId, institutionId, type, setDuplicateFlag);
        }
        else {
            //physician already exists, so insert mr id into log table
            if (institutionChangedSinceLastChecked) {
                if (shouldHaveMedicalRecordLog(conn, medicalRecordId)) {
                    writingMedicalRecordLogIntoDb(conn, medicalRecordId);
                }
            }
        }
    }

    private void writeInstitutionInfo(Connection conn, InstitutionRequest institutionRequest, String instanceId) {
        Collection<Institution> institutions = institutionRequest.getInstitutions();
        if (!institutions.isEmpty()) {
            logger.info("Participant w/ id " + institutionRequest.getParticipantId() + " has " + institutions.size() + " institutions");
            MedicalRecordUtil.writeNewRecordIntoDb(conn, SQL_INSERT_ONC_HISTORY, institutionRequest.getParticipantId(), instanceId);
            MedicalRecordUtil.writeNewRecordIntoDb(conn, SQL_INSERT_PARTICIPANT_RECORD, institutionRequest.getParticipantId(), instanceId);
            for (Institution institution : institutions) {
                MedicalRecordUtil.writeInstitutionIntoDb(conn, institutionRequest.getParticipantId(), instanceId,
                        institution.getId(), institution.getType());
            }
        }
        else {
            logger.info("Institution list was empty for participant w/ id " + institutionRequest.getParticipantId());
        }
    }

    private void writingMedicalRecordLogIntoDb(Connection conn, Number medicalRecordId) {
        if (conn != null) {
            try (PreparedStatement insertMedicalRecordLog = conn.prepareStatement(SQL_INSERT_MEDICAL_RECORD_LOG)) {
                insertMedicalRecordLog.setObject(1, medicalRecordId);
                insertMedicalRecordLog.setString(2, MedicalRecordLog.DATA_REVIEW);
                insertMedicalRecordLog.setLong(3, System.currentTimeMillis());
                int result = insertMedicalRecordLog.executeUpdate();
                if (result != 1) {
                    throw new RuntimeException("Error updating row");
                }
                logger.info("Added medical record log for medical record w/ id " + medicalRecordId);
            }
            catch (SQLException e) {
                throw new RuntimeException("Error inserting new medical record ", e);
            }
        }
        else {
            throw new RuntimeException("DB connection was null");
        }
    }

    public Collection<Number> getMedicalRecordIds(Connection conn, String participantId, String instanceId) {
        Collection<Number> medicalRecordIds = new HashSet<>();
        if (conn != null) {
            try (PreparedStatement getMedicalRecordIds = conn.prepareStatement(SQL_SELECT_MEDICAL_RECORD_LOG)) {
                getMedicalRecordIds.setString(1, participantId);
                getMedicalRecordIds.setString(2, instanceId);
                getMedicalRecordIds.setString(3, MedicalRecordLog.DATA_REVIEW);
                try (ResultSet rs = getMedicalRecordIds.executeQuery()) {
                    while (rs.next()) {
                        Number medicalRecordId = rs.getInt(DBConstants.MEDICAL_RECORD_ID);
                        String type = rs.getString(DBConstants.TYPE);
                        if ((StringUtils.isNotBlank(rs.getString(DBConstants.DATE)) && MedicalRecordLog.DATA_REVIEW.equals(type))
                                || StringUtils.isBlank(type)) {
                            medicalRecordIds.add(medicalRecordId);
                        }
                        else {
                            if (medicalRecordIds.contains(medicalRecordId)) {
                                medicalRecordIds.remove(medicalRecordId);
                            }
                        }
                    }
                }
                catch (SQLException e1) {
                    throw new RuntimeException("Error getting medicalRecordId ", e1);
                }
            }
            catch (SQLException e) {
                throw new RuntimeException("Error getting medicalRecordIds ", e);
            }
        }
        return medicalRecordIds;
    }

    public boolean shouldHaveMedicalRecordLog(Connection conn, Number medicalRecordId) {
        if (conn != null) {
            try (PreparedStatement getMedicalRecordIds = conn.prepareStatement(SQL_SELECT_LOG_FOR_MEDICAL_RECORD)) {
                getMedicalRecordIds.setInt(1, medicalRecordId.intValue());
                getMedicalRecordIds.setString(2, MedicalRecordLog.DATA_REVIEW);
                try (ResultSet rs = getMedicalRecordIds.executeQuery()) {
                    if (rs.next()) {
                        String type = rs.getString(DBConstants.TYPE);
                        if ((StringUtils.isNotBlank(rs.getString(DBConstants.DATE)) && MedicalRecordLog.DATA_REVIEW.equals(type))
                                || StringUtils.isBlank(type)) {
                            return true;
                        }
                    }
                }
                catch (SQLException e1) {
                    throw new RuntimeException("Error getting medicalRecordId ", e1);
                }
            }
            catch (SQLException e) {
                throw new RuntimeException("Error getting medicalRecordIds ", e);
            }
        }
        return false;
    }
}
