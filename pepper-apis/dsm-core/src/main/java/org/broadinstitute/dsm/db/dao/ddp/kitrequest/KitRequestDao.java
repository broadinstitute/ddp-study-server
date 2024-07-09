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
import org.broadinstitute.dsm.service.adminoperation.UpdateKitToLegacyIdsRequest;
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

    public static final String SQL_GET_SAMPLE_BY_BSP_COLLABORATOR_SAMPLE_ID = " SELECT * FROM  ddp_kit_request r"
            + " WHERE bsp_collaborator_sample_id = ?";

    public static final String SQL_GET_SAMPLE_BY_BSP_COLLABORATOR_PARTICIPANT_ID = " SELECT * FROM  ddp_kit_request r"
            + " WHERE bsp_collaborator_participant_id = ?";

    public static final String SQL_UPDATE_COLLAB_ID_AND_DDP_PARTICIPANT_ID =
            " UPDATE ddp_kit_request SET bsp_collaborator_sample_id = ?, ddp_participant_id = ?, "
            + "bsp_collaborator_participant_id = ? WHERE dsm_kit_request_id in  "
            + "(SELECT dsm_kit_request_id FROM (SELECT dsm_kit_request_id FROM ddp_kit_request) as tbl "
            + " WHERE bsp_collaborator_sample_id = ?) AND dsm_kit_request_id <> 0";

    public static final String BY_DDP_LABEL = " where ddp_label = ?";

    public static final String BY_KIT_LABEL = " left join ddp_kit k on req.dsm_kit_request_id = k.dsm_kit_request_id where kit_label = ?";

    public static final String SQL_GET_KIT_TYPE_BY_KIT_REQUEST_ID =
            "select kt.kit_type_name FROM ddp_kit_request kr "
                    + "join kit_type kt on kt.kit_type_id = kr.kit_type_id "
                    + "where kr.dsm_kit_request_id = ? ";

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
                    if (rs.next()) {
                        dbVals.resultValue = rs.getString(DBConstants.KIT_LABEL);
                    } else {
                        dbVals.resultValue = null;
                        dbVals.resultException = new DsmInternalError("No kit found for dsm_kit_request_id " + dsmKitRequestId);
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
        return (String) results.resultValue;
    }

    public String getKitTypeByKitRequestId(long kitRequestId) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_GET_KIT_TYPE_BY_KIT_REQUEST_ID)) {
                stmt.setLong(1, kitRequestId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = rs.getString(DBConstants.KIT_TYPE_NAME);
                    } else {
                        dbVals.resultValue = null;
                        dbVals.resultException = new DsmInternalError("No kit request found for kit_request_id " + kitRequestId);
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new DsmInternalError("Couldn't get kit type for kit request: " + kitRequestId, results.resultException);
        }
        return (String) results.resultValue;
    }

    /**
     * This method works as "re-patient" or "re-sample" a kit as described by gp terms
     * which is that for a participant that had kits with legacy ids, dsm changes their new kits with pepper ids to the legacy ids as well.
     * By running this method DSM also changes the ddpParticipantId to the legacy participant id to match the older kits.
     * </p><p>
     * The method updates the DSM database with the legacy collaborator sample id, collaborator participant id and also changes
     * the ddp participant id to the legacy participant id. It does not  update the ES document at this point.
     *</p>
     * @param updateKitToLegacyIdsRequest request to resample or re-patient a kit for a participant with a legacy short ID
     * @param legacyParticipantId legacy participant ID
     * */
    public void updateKitToLegacyIds(UpdateKitToLegacyIdsRequest updateKitToLegacyIdsRequest, String legacyParticipantId) {
        SimpleResult results = TransactionWrapper.inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_COLLAB_ID_AND_DDP_PARTICIPANT_ID)) {
                stmt.setString(1, updateKitToLegacyIdsRequest.getNewCollaboratorSampleId());
                stmt.setString(2, legacyParticipantId);
                stmt.setString(3, updateKitToLegacyIdsRequest.getNewCollaboratorParticipantId());
                stmt.setString(4, updateKitToLegacyIdsRequest.getCurrentCollaboratorSampleId());
                int affectedRows = stmt.executeUpdate();
                if (affectedRows != 1) {
                    // checks that only one row was updated, which means only one row has this collaborator sample id
                    throw new DSMBadRequestException(
                            "Error updating kit to legacy ids for kit with sample id %s, was updating %d rows".formatted(
                                    updateKitToLegacyIdsRequest.getCurrentCollaboratorSampleId(), affectedRows));
                }
            } catch (SQLException e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new DsmInternalError("Error resampling kit for sample id "
                    + updateKitToLegacyIdsRequest.getCurrentCollaboratorSampleId(), results.resultException);
        }
        log.info("Updated kit for sample id %s to %s ".formatted(updateKitToLegacyIdsRequest.getCurrentCollaboratorSampleId(),
                updateKitToLegacyIdsRequest.getNewCollaboratorSampleId()));
    }

    public List<KitRequestShipping> getKitRequestForCollaboratorSampleId(String collaboratorSampleId) {
        List<KitRequestShipping> kitRequestShippingList = new ArrayList<>();
        SimpleResult results = TransactionWrapper.inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_GET_SAMPLE_BY_BSP_COLLABORATOR_SAMPLE_ID)) {
                stmt.setString(1, collaboratorSampleId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        kitRequestShippingList.add(KitRequestShipping.getKitRequestFromResultSet(rs));
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
        return kitRequestShippingList;
    }

    public List<KitRequestShipping> getKitRequestsForCollaboratorParticipantId(String collaboratorParticipantId) {
        List<KitRequestShipping> kitRequestShippingList = new ArrayList<>();
        SimpleResult results = TransactionWrapper.inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_GET_SAMPLE_BY_BSP_COLLABORATOR_PARTICIPANT_ID)) {
                stmt.setString(1, collaboratorParticipantId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        kitRequestShippingList.add(KitRequestShipping.getKitRequestFromResultSet(rs));
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
            throw new DsmInternalError("Error getting KitRequestShipping by collaboratorParticipantId "
                    + collaboratorParticipantId, results.resultException);
        }
        return kitRequestShippingList;
    }
}
