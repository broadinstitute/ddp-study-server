package org.broadinstitute.dsm.db.dao.ddp.kitrequest;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dto.ddp.kitrequest.ESSamplesDto;
import org.broadinstitute.dsm.db.dto.ddp.kitrequest.KitRequestDto;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.service.adminoperation.LegacyKitResampleRequest;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.SystemUtil;
import org.broadinstitute.lddp.db.SimpleResult;

@Slf4j
public class KitRequestDao implements Dao<KitRequestDto> {

    public static final String SQL_SELECT_ES_SAMPLE =
            "SELECT  kr.ddp_participant_id,  kr.ddp_kit_request_id,  kt.kit_type_name,  dk.kit_label, "
                    + "kr.bsp_collaborator_sample_id,  kr.bsp_collaborator_participant_id,  dk.tracking_to_id, "
                    + "dk.tracking_return_id,  cs.carrier,  dk.scan_date,  dk.easypost_shipment_date,  dk.receive_date "
                    + "FROM  ddp_kit_request kr  LEFT JOIN  ddp_kit dk ON dk.dsm_kit_request_id = kr.dsm_kit_request_id "
                    + "LEFT JOIN  kit_type kt ON kr.kit_type_id = kt.kit_type_id  LEFT JOIN "
                    + "ddp_kit_request_settings krs ON (kr.ddp_instance_id = krs.ddp_instance_id "
                    + "AND kr.kit_type_id = krs.kit_type_id)  LEFT JOIN "
                    + "carrier_service cs ON (krs.carrier_service_to_id = cs.carrier_service_id)";

    public static final String SQL_GET_KIT_LABEL =
            "select kit_label from ddp_kit where dsm_kit_request_id = ?";

    public static final String BY_INSTANCE_ID = " WHERE kr.ddp_instance_id = ?";

    public static final String SQL_GET_KIT_REQUEST =
            "SELECT  req.ddp_kit_request_id,   req.ddp_instance_id,  req.ddp_kit_request_id,  req.kit_type_id,"
                    + "req.bsp_collaborator_participant_id,  req.bsp_collaborator_sample_id,  req.ddp_participant_id, "
                    + "req.ddp_label,  req.created_by,  req.created_date,  req.external_order_number, "
                    + "req.external_order_date,  req.external_order_status,  req.external_response,  req.upload_reason, "
                    + "req.order_transmitted_at,  req.dsm_kit_request_id  FROM  ddp_kit_request req";

    public static final String SQL_GET_SAMPLE_BY_BSP_COLLABORATOR_SAMPLE_ID = " SELECT * FROM  ddp_kit_request r LEFT JOIN ddp_kit k "
            + " ON (r.dsm_kit_request_id = k.dsm_kit_request_id) WHERE bsp_collaborator_sample_id = ?";
    public static final String SQL_RESAMPLE_KIT = " UPDATE ddp_kit_request SET bsp_collaborator_sample_id = ?, ddp_participant_id = ?, "
            + "bsp_collaborator_participant_id = ? WHERE dsm_kit_request_id in = "
            + "(SELECT dsm_kit_request_id FROM ddp_kit_request WHERE bsp_collaborator_sample_id = ?) AND dsm_kit_request_id <> 0";

    public static final String BY_DDP_LABEL = " where ddp_label = ?";

    public static final String BY_KIT_LABEL = " left join ddp_kit k on req.dsm_kit_request_id = k.dsm_kit_request_id where kit_label = ?";

    @Override
    public int create(KitRequestDto kitRequestDto) {
        return 0;
    }

    @Override
    public int delete(int id) {
        return 0;
    }

    @Override
    public Optional<KitRequestDto> get(long id) {
        return Optional.empty();
    }

    public List<ESSamplesDto> getESSamplesByInstanceId(int instanceId) {
        List<ESSamplesDto> samplesDtosListES = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult execResult = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_ES_SAMPLE + BY_INSTANCE_ID)) {
                stmt.setInt(1, instanceId);
                try (ResultSet ESSampleRs = stmt.executeQuery()) {
                    while (ESSampleRs.next()) {
                        samplesDtosListES.add(new ESSamplesDto(ESSampleRs.getString(DBConstants.DDP_PARTICIPANT_ID),
                                ESSampleRs.getString(DBConstants.DDP_KIT_REQUEST_ID), ESSampleRs.getString(DBConstants.KIT_TYPE_NAME),
                                ESSampleRs.getString(DBConstants.KIT_LABEL),
                                ESSampleRs.getString(DBConstants.BSP_COLLABORATOR_SAMPLE_ID),
                                ESSampleRs.getString(DBConstants.COLLABORATOR_PARTICIPANT_ID),
                                ESSampleRs.getString(DBConstants.DSM_TRACKING_TO), ESSampleRs.getString(DBConstants.DSM_TRACKING_RETURN),
                                ESSampleRs.getString(DBConstants.CARRIER),
                                SystemUtil.getNullOrDateFormatted(ESSampleRs.getLong(DBConstants.DSM_SCAN_DATE)),
                                SystemUtil.getNullOrDateFormatted(ESSampleRs.getLong(DBConstants.EASYPOST_SHIPMENT_DATE)),
                                SystemUtil.getNullOrDateFormatted(ESSampleRs.getLong(DBConstants.DSM_RECEIVE_DATE))));
                    }
                }
            } catch (SQLException ex) {
                execResult.resultException = ex;
            }
            return execResult;
        });
        if (results.resultException != null) {
            throw new RuntimeException("Error getting samples by instanceId " + instanceId, results.resultException);
        }
        return samplesDtosListES;
    }

    public String getKitLabelFromDsmKitRequestId(long dsmKitRequestId) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_GET_KIT_LABEL)) {
                stmt.setLong(1, dsmKitRequestId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        dbVals.resultValue = rs.getString(DBConstants.KIT_LABEL);
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Couldn't get kit label for kit " + dsmKitRequestId, results.resultException);
        }
        String kitLabel = String.valueOf(results.resultValue);
        log.info("Got " + kitLabel + " sequencing kit label in DSM DB for " + dsmKitRequestId);
        return kitLabel;
    }

    public void resampleKit(LegacyKitResampleRequest legacyKitResampleRequest, String altPid) {
        SimpleResult results = TransactionWrapper.inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_RESAMPLE_KIT)) {
                stmt.setString(1, legacyKitResampleRequest.getNewCollaboratorSampleId());
                stmt.setString(2, altPid);
                stmt.setString(3, legacyKitResampleRequest.getNewCollaboratorSampleId());
                stmt.setString(4, legacyKitResampleRequest.getCurrentCollaboratorSampleId());
                int affectedRows = stmt.executeUpdate();
                if (affectedRows != 1) {
                    dbVals.resultException = new DSMBadRequestException("Error resampling kit for sample id %s, was updating %d rows"
                            .formatted(legacyKitResampleRequest.getCurrentCollaboratorSampleId(), affectedRows));
                    conn.rollback();
                }
            } catch (SQLException e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new DsmInternalError("Error resampling kit for sample id " + legacyKitResampleRequest.getCurrentCollaboratorSampleId(),
                    results.resultException);
        }
        log.info("Resampled kit for sample id %s to %s ".formatted(legacyKitResampleRequest.getCurrentCollaboratorSampleId(),
                legacyKitResampleRequest.getNewCollaboratorSampleId()));
    }

    public boolean hasKitRequestWithCollaboratorSampleId(String currentCollaboratorSampleId, String ddpParticipantId) {
        SimpleResult results = TransactionWrapper.inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_GET_SAMPLE_BY_BSP_COLLABORATOR_SAMPLE_ID)) {
                stmt.setString(1, currentCollaboratorSampleId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        if (ddpParticipantId.equals(rs.getString(DBConstants.DDP_PARTICIPANT_ID))) {
                            log.info("Kit request exists for collaborator sample id %s with dsm_kit_request_id %s "
                                    .formatted(currentCollaboratorSampleId, rs.getString(DBConstants.DSM_KIT_REQUEST_ID)));
                            dbVals.resultValue = true;
                            return dbVals;
                        } else {
                            dbVals.resultException = new DSMBadRequestException("ddpParticipantId %s does not belong to sample with id %s"
                                    .formatted(ddpParticipantId, currentCollaboratorSampleId));
                            return dbVals;
                        }
                    }
                    dbVals.resultValue = false;
                    return dbVals;
                }
            } catch (SQLException e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new DsmInternalError("Error checking if kit request exists for collaborator sample id "
                    + currentCollaboratorSampleId, results.resultException);
        }
        return (boolean) results.resultValue;
    }

    public KitRequestShipping getKitRequestWithCollaboratorSampleId(String collaboratorSampleId) {
        SimpleResult results = TransactionWrapper.inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_GET_SAMPLE_BY_BSP_COLLABORATOR_SAMPLE_ID)) {
                stmt.setString(1, collaboratorSampleId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = KitRequestShipping.getKitRequestShipping(rs);
                    }
                    return dbVals;
                } catch (SQLException e) {
                    dbVals.resultException = e;
                }
            } catch (SQLException e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new DsmInternalError("Error getting KitRequestShipping by collaboratorSampleId "
                    + collaboratorSampleId, results.resultException);
        }
        return (KitRequestShipping) results.resultValue;
    }
}
