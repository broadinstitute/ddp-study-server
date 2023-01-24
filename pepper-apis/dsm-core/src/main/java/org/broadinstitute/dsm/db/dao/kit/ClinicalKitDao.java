package org.broadinstitute.dsm.db.dao.kit;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.queue.EventDao;
import org.broadinstitute.dsm.db.dao.settings.EventTypeDao;
import org.broadinstitute.dsm.db.dto.kit.ClinicalKitDto;
import org.broadinstitute.dsm.db.dto.settings.EventTypeDto;
import org.broadinstitute.dsm.model.gp.ClinicalKitWrapper;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.EventUtil;
import org.broadinstitute.lddp.db.SimpleResult;

@Slf4j
public class ClinicalKitDao {
    public static final String PECGS = "PE-CGS";
    public static final String MERCURY = "MERCURY";
    private static final String SQL_GET_CLINICAL_KIT_BASED_ON_SM_ID_VALUE =
            "SELECT p.ddp_participant_id, accession_number, ddp.instance_name, t.collaborator_sample_id, date_px,  "
                    + "kit_type_name, bsp_material_type, bsp_receptacle_type, ddp.ddp_instance_id FROM sm_id sm "
                    + "LEFT JOIN ddp_tissue t on (t.tissue_id  = sm.tissue_id) "
                    + "LEFT JOIN ddp_onc_history_detail oD on (oD.onc_history_detail_id = t.onc_history_detail_id) "
                    + "LEFT JOIN ddp_medical_record mr on (mr.medical_record_id = oD.medical_record_id) "
                    + "LEFT JOIN ddp_institution inst on  (mr.institution_id = inst.institution_id AND NOT mr.deleted <=> 1) "
                    + "LEFT JOIN ddp_participant as p on (p.participant_id = inst.participant_id) "
                    + "LEFT JOIN ddp_instance as ddp on (ddp.ddp_instance_id = p.ddp_instance_id) "
                    + "LEFT JOIN sm_id_type sit on (sit.sm_id_type_id = sm.sm_id_type_id) "
                    + "LEFT JOIN kit_type ktype on ( sit.kit_type_id = ktype.kit_type_id) "
                    + "WHERE sm.sm_id_value = ? AND NOT sm.deleted <=> 1 ";
    private static final String SQL_GET_RECEIVED_CLINICAL_TISSUE_BY_DDP_PARTICIPANT_ID = "SELECT sm.received_date FROM sm_id sm "
                    + "LEFT JOIN ddp_tissue t on (t.tissue_id  = sm.tissue_id) "
                    + "LEFT JOIN ddp_onc_history_detail oD on (oD.onc_history_detail_id = t.onc_history_detail_id) "
                    + "LEFT JOIN ddp_medical_record mr on (mr.medical_record_id = oD.medical_record_id) "
                    + "LEFT JOIN ddp_institution inst on  (mr.institution_id = inst.institution_id AND NOT mr.deleted <=> 1) "
                    + "LEFT JOIN ddp_participant as p on (p.participant_id = inst.participant_id) "
                    + "LEFT JOIN ddp_instance as ddp on (ddp.ddp_instance_id = p.ddp_instance_id) "
                    + "LEFT JOIN sm_id_type sit on (sit.sm_id_type_id = sm.sm_id_type_id) "
                    + "LEFT JOIN kit_type ktype on ( sit.kit_type_id = ktype.kit_type_id) "
                    + "WHERE p.ddp_participant_id = ? AND ddp.instance_name = ? AND NOT sm.deleted <=> 1 AND sm.received_date IS NOT NULL ";

    public static final String SQL_GET_RECEIVED_CLINICAL_KITS_BY_DDP_PARTICIPANT_ID = "SELECT receive_date "
                    + "FROM ddp_kit_request req LEFT JOIN ddp_kit kit ON (req.dsm_kit_request_id = kit.dsm_kit_request_id) "
                    + "LEFT JOIN ddp_instance realm ON (realm.ddp_instance_id = req.ddp_instance_id) "
                    + "LEFT JOIN kit_type ty ON (req.kit_type_id = ty.kit_type_id) "
                    + "WHERE req.ddp_participant_id = ? AND realm.instance_name = ? AND receive_date IS NOT NULL AND receive_by = ?";
    private static final String SQL_SET_ACCESSION_TIME = "UPDATE sm_id SET received_date = ?, received_by = ? WHERE sm_id_value = ? "
                    + "AND NOT deleted <=> 1";

    public Optional<ClinicalKitWrapper> getClinicalKitFromSMId(String smIdValue) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_GET_CLINICAL_KIT_BASED_ON_SM_ID_VALUE)) {
                stmt.setString(1, smIdValue);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        ClinicalKitDto clinicalKitDto = new ClinicalKitDto(null,
                                rs.getString(DBConstants.DDP_TISSUE_ALIAS + "." + DBConstants.COLLABORATOR_SAMPLE_ID), PECGS,
                                rs.getString(DBConstants.BSP_MATERIAL_TYPE), rs.getString(DBConstants.BSP_RECEPTABLE_TYPE), null, null,
                                null, null, null, rs.getString(DBConstants.ACCESSION_NUMBER), null);
                        clinicalKitDto.setSampleType(rs.getString(DBConstants.KIT_TYPE_NAME));
                        clinicalKitDto.setCollectionDate(rs.getString(DBConstants.DATE_PX));
                        ClinicalKitWrapper clinicalKitWrapper =
                                new ClinicalKitWrapper(clinicalKitDto, Integer.parseInt(rs.getString(DBConstants.DDP_INSTANCE_ID)),
                                        rs.getString(DBConstants.DDP_PARTICIPANT_ID));
                        dbVals.resultValue = clinicalKitWrapper;
                        log.info("found clinical kit for sm id value: " + smIdValue);
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Error getting clinical kit", e);
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }

            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting clinicalKit based on smId " + smIdValue, results.resultException);
        }
        return Optional.ofNullable((ClinicalKitWrapper) results.resultValue);
    }

    public ClinicalKitDto getClinicalKitBasedOnSmId(String smIdValue) {
        log.info("Checking the kit for SM Id value " + smIdValue);
        Optional<ClinicalKitWrapper> maybeClinicalKitWrapper = getClinicalKitFromSMId(smIdValue);
        maybeClinicalKitWrapper.orElseThrow();
        ClinicalKitWrapper clinicalKitWrapper = maybeClinicalKitWrapper.get();
        ClinicalKitDto clinicalKitDto = clinicalKitWrapper.getClinicalKitDto();
        DDPInstance ddpInstance = DDPInstance.getDDPInstanceById(clinicalKitWrapper.getDdpInstanceId());
        clinicalKitDto.setNecessaryParticipantDataToClinicalKit(clinicalKitWrapper.getDdpParticipantId(), ddpInstance);
        if (StringUtils.isNotBlank(clinicalKitDto.getAccessionNumber())) {
            setAccessionTimeForSMID(smIdValue);
            if (hasKitsAccessioned(clinicalKitWrapper.getDdpParticipantId(), ddpInstance)) {
                triggerParticipantEvent(ddpInstance, clinicalKitWrapper.getDdpParticipantId(), DBConstants.REQUIRED_SAMPLES_RECEIVED_EVENT);
            }
            return clinicalKitDto;
        }
        throw new RuntimeException("The kit doesn't have an accession number! SM ID is: " + smIdValue);
    }

    private void setAccessionTimeForSMID(String smIdValue) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SET_ACCESSION_TIME)) {
                stmt.setLong(1, System.currentTimeMillis());
                stmt.setString(2, MERCURY);
                stmt.setString(3, smIdValue);
                int r = stmt.executeUpdate();
                if (r != 1) { //number of sm ids with that value
                    throw new RuntimeException(
                            "Update query for smId accession time updated " + r + " rows! with smId value " + smIdValue);
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }

            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error updating accession time for smId " + smIdValue, results.resultException);
        }
    }

    public static void ifTissueAccessionedTriggerDDP(String ddpParticipantId, DDPInstance ddpInstance) {
        if (hasTissueAccessioned(ddpParticipantId, ddpInstance)) {
            triggerParticipantEvent(ddpInstance, ddpParticipantId, DBConstants.REQUIRED_SAMPLES_RECEIVED_EVENT);
        }
    }

    private static boolean hasTissueAccessioned(String ddpParticipantId, DDPInstance ddpInstance) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult(0);
            try (PreparedStatement stmt = conn.prepareStatement(SQL_GET_RECEIVED_CLINICAL_TISSUE_BY_DDP_PARTICIPANT_ID)) {
                stmt.setString(1, ddpParticipantId);
                stmt.setString(2, ddpInstance.getName());
                try (ResultSet rs = stmt.executeQuery()) {
                    rs.last();
                    dbVals.resultValue = rs.getRow();
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            log.error("Couldn't get tissue sample information for pt w/ id " + ddpParticipantId, results.resultException);
        }
        if ((int) results.resultValue > 0) {
            return true;
        }
        return false;
    }

    public boolean hasKitsAccessioned(String ddpParticipantId, DDPInstance ddpInstance) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult(0);
            try (PreparedStatement stmt = conn.prepareStatement(SQL_GET_RECEIVED_CLINICAL_KITS_BY_DDP_PARTICIPANT_ID)) {
                stmt.setString(1, ddpParticipantId);
                stmt.setString(2, ddpInstance.getName());
                stmt.setString(3, MERCURY);
                try (ResultSet rs = stmt.executeQuery()) {
                    rs.last();
                    dbVals.resultValue = rs.getRow();
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            log.error("Couldn't get tissue sample information for pt w/ id " + ddpParticipantId, results.resultException);
        }
        if ((int) results.resultValue > 0) {
            return true;
        }
        return false;
    }

    private static void triggerParticipantEvent(DDPInstance ddpInstance, String ddpParticipantId, String eventName) {
        final EventDao eventDao = new EventDao();
        final EventTypeDao eventTypeDao = new EventTypeDao();
        Optional<EventTypeDto> eventType =
                eventTypeDao.getEventTypeByEventNameAndInstanceId(eventName, ddpInstance.getDdpInstanceId());
        eventType.ifPresent(eventTypeDto -> {
            boolean participantHasTriggeredEventByEventType =
                    eventDao.hasTriggeredEventByEventTypeAndDdpParticipantId(eventName, ddpParticipantId).orElse(false);
            if (!participantHasTriggeredEventByEventType) {
                inTransaction((conn) -> {
                    EventUtil.triggerDDP(conn, eventType, ddpParticipantId);
                    return null;
                });
            } else {
                log.info("Participant " + ddpParticipantId + " was already triggered for event type " + eventName);
            }
        });
    }
}
