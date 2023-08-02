package org.broadinstitute.dsm.db.dao.kit;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.model.kit.ScanError;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.DBUtil;
import org.broadinstitute.dsm.util.KitUtil;
import org.broadinstitute.lddp.db.SimpleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KitDaoImpl implements KitDao {

    public static final String SQL_SELECT_KIT_REQUEST =
            "SELECT * FROM ( SELECT req.upload_reason, kt.kit_type_name, ddp_site.instance_name, ddp_site.ddp_instance_id, "
                    + "ddp_site.base_url, ddp_site.auth0_token, ddp_site.billing_reference, "
                    + "ddp_site.migrated_ddp, ddp_site.collaborator_id_prefix, ddp_site.es_participant_index, "
                    + "req.bsp_collaborator_participant_id, req.bsp_collaborator_sample_id, req.ddp_participant_id, req.ddp_label, "
                    + "req.dsm_kit_request_id, "
                    + "req.kit_type_id, req.external_order_status, req.external_order_number, req.external_order_date, "
                    + "req.external_response, kt.no_return, req.created_by FROM kit_type kt, ddp_kit_request req, ddp_instance ddp_site "
                    + "WHERE req.ddp_instance_id = ddp_site.ddp_instance_id AND req.kit_type_id = kt.kit_type_id) AS request "
                    + "LEFT JOIN (SELECT * FROM (SELECT kit.dsm_kit_request_id, kit.dsm_kit_id, kit.kit_complete, kit.label_url_to, "
                    + "kit.label_url_return, kit.tracking_to_id, "
                    + "kit.tracking_return_id, kit.easypost_tracking_to_url, kit.easypost_tracking_return_url, kit.easypost_to_id, "
                    + "kit.easypost_shipment_status, kit.scan_date, kit.label_date, kit.error, kit.message, "
                    + "kit.receive_date, kit.deactivated_date, kit.easypost_address_id_to, kit.deactivation_reason, tracking.tracking_id,"
                    + " kit.kit_label, kit.express, kit.test_result, kit.needs_approval, kit.authorization, kit.denial_reason, "
                    + "kit.authorized_by, kit.ups_tracking_status, kit.ups_return_status, kit.CE_order FROM ddp_kit kit "
                    + "INNER JOIN (SELECT dsm_kit_request_id, MAX(dsm_kit_id) AS kit_id FROM ddp_kit "
                    + "GROUP BY dsm_kit_request_id) groupedKit ON kit.dsm_kit_request_id = groupedKit.dsm_kit_request_id "
                    + "AND kit.dsm_kit_id = groupedKit.kit_id LEFT JOIN ddp_kit_tracking tracking "
                    + "ON (kit.kit_label = tracking.kit_label))as wtf) AS kit ON kit.dsm_kit_request_id = request.dsm_kit_request_id "
                    + "LEFT JOIN ddp_participant_exit ex ON (ex.ddp_instance_id = request.ddp_instance_id "
                    + "AND ex.ddp_participant_id = request.ddp_participant_id) "
                    + "LEFT JOIN ddp_kit_request_settings dkc ON (request.ddp_instance_id = dkc.ddp_instance_id "
                    + "AND request.kit_type_id = dkc.kit_type_id) WHERE ex.ddp_participant_exit_id is null";
    public static final String KIT_BY_KIT_REQUEST_ID = " and kit.dsm_kit_request_id = ?";
    public static final String KIT_BY_KIT_ID = " and kit.dsm_kit_id = ?";
    public static final String KIT_BY_HRUID = " and bsp_collaborator_participant_id like ? AND not kit_complete <=> 1 "
            + "AND deactivated_date is null";
    private static final Logger logger = LoggerFactory.getLogger(KitDaoImpl.class);
    private static final String SQL_IS_BLOOD_KIT_QUERY = "SELECT kt.requires_insert_in_kit_tracking AS found "
            + "FROM ddp_kit_request request "
            + "LEFT JOIN kit_type kt on (kt.kit_type_id = request.kit_type_id) "
            + "WHERE ddp_label = ?";

    private static final String SQL_HAS_KIT_TRACKING = "SELECT 1 AS found "
            + "from "
            + "(SELECT 1 FROM "
            + "ddp_kit_tracking tracking "
            + "WHERE tracking.kit_label = ?) AS existing_rows";
    private static final String UPDATE_KIT_REQUEST = "UPDATE ddp_kit SET "
            + "kit_complete = 1, scan_date = ?, scan_by = ?, kit_label = ? "
            + "WHERE "
            + "dsm_kit_request_id = (SELECT dsm_kit_request_id FROM ddp_kit_request WHERE ddp_label = ?) "
            + "AND not kit_complete <=> 1 "
            + "AND deactivated_date is null";
    private static final String UPDATE_KIT_LABEL = "UPDATE ddp_kit SET "
            + "kit_label = ? "
            + "WHERE "
            + "dsm_kit_id = ?";

    private static final String INSERT_KIT_TRACKING = "INSERT INTO "
            + "ddp_kit_tracking "
            + "SET "
            + "scan_date = ?, scan_by = ?, tracking_id = ?, kit_label = ?";

    private static final String SQL_GET_KIT_BY_DDP_LABEL = "SELECT req.ddp_kit_request_id, req.ddp_instance_id, req.ddp_kit_request_id, "
            + "req.kit_type_id, req.bsp_collaborator_participant_id, req.bsp_collaborator_sample_id, req.ddp_participant_id, "
            + "req.ddp_label, req.created_by, req.created_date, req.external_order_number, "
            + "req.external_order_date, req.external_order_status, req.external_response, req.upload_reason, "
            + "req.order_transmitted_at, req.dsm_kit_request_id, kit.kit_label, kit.dsm_kit_id, kit.message, "
            + "kt.requires_insert_in_kit_tracking, track.tracking_id, ks.kit_label_prefix, ks.kit_label_length "
            + "FROM ddp_kit as kit "
            + "LEFT JOIN ddp_kit_request AS req ON req.dsm_kit_request_id = kit.dsm_kit_request_id "
            + "LEFT JOIN ddp_kit_tracking AS track ON track.kit_label = ?"
            + "LEFT JOIN kit_type AS kt ON kt.kit_type_id = req.kit_type_id "
            + "LEFT JOIN ddp_kit_request_settings AS ks ON ks.kit_type_id = req.kit_type_id AND ks.ddp_instance_id = req.ddp_instance_id "
            + "WHERE req.ddp_label = ?";

    private static final String SQL_GET_SUB_KIT_BY_DDP_LABEL =
            "SELECT req.ddp_kit_request_id, req.ddp_instance_id, req.ddp_kit_request_id, "
                    + "req.kit_type_id, req.bsp_collaborator_participant_id, req.bsp_collaborator_sample_id, req.ddp_participant_id, "
                    + "req.ddp_label, req.created_by, req.created_date, req.external_order_number, "
                    + "req.external_order_date, req.external_order_status, req.external_response, req.upload_reason, "
                    + "req.order_transmitted_at, req.dsm_kit_request_id, kit.kit_label, kit.dsm_kit_id,"
                    + "kt.requires_insert_in_kit_tracking, kt.kit_type_name, track.tracking_id, ks.kit_label_prefix, ks.kit_label_length "
                    + "FROM ddp_kit as kit "
                    + "LEFT JOIN ddp_kit_request AS req ON req.dsm_kit_request_id = kit.dsm_kit_request_id "
                    + "LEFT JOIN ddp_kit_tracking AS track ON track.kit_label = ?"
                    + "LEFT JOIN kit_type AS kt ON kt.kit_type_id = req.kit_type_id "
                    +
                    "LEFT JOIN ddp_kit_request_settings AS ks ON ks.kit_type_id = req.kit_type_id AND ks.ddp_instance_id = req.ddp_instance_id "
                    + "WHERE ( req.ddp_label = ? or ddp_label like ? )";

    private static final String INSERT_KIT = "INSERT INTO "
            + "ddp_kit "
            + "(dsm_kit_request_id, "
            + "kit_label, "
            + "label_url_to, "
            + "label_url_return, "
            + "easypost_to_id, "
            + "easypost_return_id, "
            + "tracking_to_id, "
            + "tracking_return_id, "
            + "easypost_tracking_to_url, "
            + "easypost_tracking_return_url, "
            + "error, "
            + "message, "
            + "easypost_address_id_to) "
            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";

    private static final String INSERT_KIT_REQUEST = "INSERT INTO "
            + "ddp_kit_request "
            + "(ddp_instance_id, "
            + "ddp_kit_request_id, "
            + "kit_type_id, "
            + "ddp_participant_id, "
            + "bsp_collaborator_participant_id, "
            + "bsp_collaborator_sample_id, "
            + "ddp_label, "
            + "created_by, "
            + "created_date, "
            + "external_order_number, "
            + "upload_reason) "
            + "VALUES (?,?,?,?,?,?,?,?,?,?,?)";

    private static final String SELECT_KIT_STATUS =
            " SELECT req.*, k.*, discard.*, tracking.tracking_id as return_tracking_number, tracking.scan_by as tracking_scan_by, "
                    + " tracking.scan_date as tracking_scan_date "
                    + " FROM ddp_kit_request req LEFT JOIN ddp_kit k on (k.dsm_kit_request_id = req.dsm_kit_request_id) "
                    + " LEFT JOIN ddp_kit_discard discard on  (discard.dsm_kit_request_id = req.dsm_kit_request_id) "
                    + " LEFT JOIN ddp_kit_tracking tracking on  (tracking.kit_label = k.kit_label) ";

    private static final String BY_INSTANCE_ID = " WHERE ddp_instance_id = ? ";
    private static final String BY_JUNIPER_KIT_ID = " WHERE ddp_kit_request_id = ? ";
    private static final String BY_PARTICIPANT_ID = " WHERE ddp_participant_id = ? ";


    private static final String SQL_SELECT_RECEIVED_KITS = " SELECT receive_date FROM ddp_kit k LEFT JOIN ddp_kit_request r "
            + " ON (k.dsm_kit_request_id  = r.dsm_kit_request_id) WHERE ddp_participant_id = ? AND receive_date IS NOT NULL ";

    private static final String SQL_DELETE_KIT_REQUEST = "DELETE FROM ddp_kit_request WHERE dsm_kit_request_id = ?";

    private static final String SQL_DELETE_KIT = "DELETE FROM ddp_kit WHERE dsm_kit_id = ?";

    private static final String UPDATE_KIT_RECEIVED = KitUtil.SQL_UPDATE_KIT_RECEIVED;

    @Override
    public int create(KitRequestShipping kitRequestDto) {
        return 0;
    }

    @Override
    public int delete(int id) {
        return 0;
    }

    @Override
    public Optional<KitRequestShipping> get(long id) {
        return Optional.empty();
    }

    @Override
    public Boolean isBloodKit(String ddpLabel) {
        return booleanCheckFoundAsName(ddpLabel, SQL_IS_BLOOD_KIT_QUERY);
    }


    @Override
    public Boolean hasTrackingScan(String kitLabel) {
        return booleanCheckFoundAsName(kitLabel, SQL_HAS_KIT_TRACKING);
    }

    @Override
    public Optional<ScanError> updateKitRequest(KitRequestShipping kitRequestShipping, String userId) {
        Optional<ScanError> result = Optional.empty();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(UPDATE_KIT_REQUEST)) {
                stmt.setLong(1, System.currentTimeMillis());
                stmt.setString(2, userId);
                stmt.setString(3, kitRequestShipping.getKitLabel());
                stmt.setString(4, kitRequestShipping.getDdpLabel());
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected != 1) {
                    dbVals.resultValue = new ScanError(kitRequestShipping.getDdpLabel(), "ddp_label "
                            + kitRequestShipping.getDdpLabel() + " does not exist or already has a Kit Label");
                } else {
                    logger.info("Updated kitRequests w/ ddp_label " + kitRequestShipping.getDdpLabel());
                    if (StringUtils.isNotBlank(kitRequestShipping.getBspCollaboratorParticipantId())) {
                        dbVals.resultValue = new ScanError(kitRequestShipping.getDdpLabel(), null,
                                kitRequestShipping.getBspCollaboratorParticipantId());
                    }
                }
            } catch (Exception ex) {
                dbVals.resultValue = new ScanError(kitRequestShipping.getDdpLabel(),
                        "Kit Label \"" + kitRequestShipping.getDdpLabel() + "\" was already scanned.\n"
                                + UserErrorMessages.IF_QUESTIONS_CONTACT_DEVELOPER);
            }
            return dbVals;
        });
        if (Objects.nonNull(results.resultValue)) {
            result = Optional.ofNullable((ScanError) results.resultValue);
        }
        return result;
    }

    @Override
    public Optional<ScanError> updateKitReceived(KitRequestShipping kitRequestShipping,
                                                 String userId) {
        Optional<ScanError> result = Optional.empty();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(UPDATE_KIT_RECEIVED)) {
                stmt.setLong(1, System.currentTimeMillis());
                stmt.setString(2, userId);
                stmt.setString(3, kitRequestShipping.getKitLabel());
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected != 1) {
                    dbVals.resultValue = Optional.of(new ScanError(kitRequestShipping.getKitLabel(),
                            "SM-ID \"" + kitRequestShipping.getKitLabel() + "\" does not exist or was already scanned as received.\n"
                                    + UserErrorMessages.IF_QUESTIONS_CONTACT_DEVELOPER));
                }
            } catch (Exception ex) {
                dbVals.resultValue = Optional.of(new ScanError(kitRequestShipping.getKitLabel(),
                        "SM-ID \"" + kitRequestShipping.getKitLabel() + "\" does not exist or was already scanned as received.\n"
                                + UserErrorMessages.IF_QUESTIONS_CONTACT_DEVELOPER));
            }
            return dbVals;
        });
        if (Objects.nonNull(results.resultValue)) {
            result = (Optional<ScanError>) results.resultValue;
        }
        return result;
    }

    @Override
    public Integer insertKit(KitRequestShipping kitRequestShipping) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(INSERT_KIT, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setLong(1, kitRequestShipping.getDsmKitRequestId());
                stmt.setString(2, kitRequestShipping.getKitLabel());
                stmt.setString(3, kitRequestShipping.getLabelUrlTo());
                stmt.setString(4, kitRequestShipping.getLabelUrlReturn());
                stmt.setString(5, kitRequestShipping.getEasypostToId());
                stmt.setString(6, kitRequestShipping.getEasypostShipmentStatus());
                stmt.setString(7, kitRequestShipping.getTrackingToId());
                stmt.setString(8, kitRequestShipping.getTrackingReturnId());
                stmt.setString(9, kitRequestShipping.getEasypostTrackingToUrl());
                stmt.setString(10, kitRequestShipping.getEasypostTrackingReturnUrl());
                stmt.setBoolean(11, kitRequestShipping.getError());
                stmt.setString(12, kitRequestShipping.getMessage());
                stmt.setString(13, kitRequestShipping.getEasypostAddressId());
                stmt.executeUpdate();
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        dbVals.resultValue = rs.getInt(1);
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });
        if (Objects.nonNull(results.resultException)) {
            throw new RuntimeException("Error inserting kit with dsm_kit_request_id: "
                    + kitRequestShipping.getDsmKitRequestId(), results.resultException);
        }
        return (int) results.resultValue;
    }

    @Override
    public Integer insertKitRequest(KitRequestShipping kitRequestShipping) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(INSERT_KIT_REQUEST, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setLong(1, kitRequestShipping.getDdpInstanceId());
                stmt.setString(2, kitRequestShipping.getDdpKitRequestId());
                stmt.setString(3, kitRequestShipping.getKitTypeId());
                stmt.setString(4, kitRequestShipping.getDdpParticipantId());
                stmt.setString(5, kitRequestShipping.getBspCollaboratorParticipantId());
                stmt.setString(6, kitRequestShipping.getBspCollaboratorSampleId());
                stmt.setString(7, kitRequestShipping.getDdpLabel());
                stmt.setString(8, kitRequestShipping.getCreatedBy());
                stmt.setLong(9, kitRequestShipping.getCreatedDate());
                stmt.setString(10, kitRequestShipping.getExternalOrderNumber());
                stmt.setString(11, kitRequestShipping.getUploadReason());
                stmt.executeUpdate();
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        dbVals.resultValue = rs.getInt(1);
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });
        if (Objects.nonNull(results.resultException)) {
            throw new RuntimeException("Error inserting kit request for participant id: "
                    + kitRequestShipping.getDdpParticipantId(), results.resultException);
        }
        return (int) results.resultValue;
    }

    @Override
    public Optional<KitRequestShipping> getKitRequest(Long kitRequestId) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_KIT_REQUEST + KIT_BY_KIT_REQUEST_ID)) {
                stmt.setLong(1, kitRequestId);
                try (ResultSet rs = stmt.executeQuery()) {
                    int numRows = 0;
                    while (rs.next()) {
                        numRows++;
                        dbVals.resultValue = getKitRequestShipping(rs);
                    }
                    if (numRows > 1) {
                        throw new RuntimeException("Found " + numRows + " kits for dsm_kit_request_id " + kitRequestId);
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error setting kitRequest to deactivated w/ dsm_kit_request_id " + kitRequestId,
                    results.resultException);
        }
        return Optional.ofNullable((KitRequestShipping) results.resultValue);
    }

    @Override
    public Optional<KitRequestShipping> getKit(Long kitId) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_KIT_REQUEST + KIT_BY_KIT_ID)) {
                stmt.setLong(1, kitId);
                try (ResultSet rs = stmt.executeQuery()) {
                    int numRows = 0;
                    while (rs.next()) {
                        numRows++;
                        dbVals.resultValue = getKitRequestShipping(rs);
                    }
                    if (numRows > 1) {
                        throw new RuntimeException("Found " + numRows + " kits for dsm_kit_request_id " + kitId);
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error setting kitRequest to deactivated w/ dsm_kit_request_id " + kitId,
                    results.resultException);
        }
        return Optional.ofNullable((KitRequestShipping) results.resultValue);
    }

    @Override
    public Integer deleteKitRequest(Long kitRequestId) {
        SimpleResult simpleResult = inTransaction(conn -> {
            SimpleResult execResult = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_DELETE_KIT_REQUEST)) {
                stmt.setLong(1, kitRequestId);
                execResult.resultValue = stmt.executeUpdate();
            } catch (SQLException sqle) {
                execResult.resultException = sqle;
            }
            return execResult;
        });

        if (simpleResult.resultException != null) {
            throw new RuntimeException("Error deleting kit request with id: " + kitRequestId, simpleResult.resultException);
        }
        return (int) simpleResult.resultValue;
    }

    @Override
    public Integer deleteKit(Long kitId) {
        SimpleResult simpleResult = inTransaction(conn -> {
            SimpleResult execResult = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_DELETE_KIT)) {
                stmt.setLong(1, kitId);
                execResult.resultValue = stmt.executeUpdate();
            } catch (SQLException sqle) {
                execResult.resultException = sqle;
            }
            return execResult;
        });

        if (simpleResult.resultException != null) {
            throw new RuntimeException("Error deleting kit with id: " + kitId, simpleResult.resultException);
        }
        return (int) simpleResult.resultValue;
    }

    @Override
    public Optional<KitRequestShipping> getKitByDdpLabel(String ddpLabel, String kitLabel) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_GET_KIT_BY_DDP_LABEL)) {
                stmt.setString(1, kitLabel);
                stmt.setString(2, ddpLabel);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        KitRequestShipping kitRequestShipping = new KitRequestShipping();
                        kitRequestShipping.setDsmKitRequestId(rs.getLong(DBConstants.DSM_KIT_REQUEST_ID));
                        kitRequestShipping.setDdpInstanceId(rs.getLong(DBConstants.DDP_INSTANCE_ID));
                        kitRequestShipping.setDdpKitRequestId(rs.getString(DBConstants.DDP_KIT_REQUEST_ID));
                        kitRequestShipping.setKitTypeId(String.valueOf(rs.getInt(DBConstants.KIT_TYPE_ID)));
                        kitRequestShipping.setBspCollaboratorParticipantId(rs.getString(DBConstants.COLLABORATOR_PARTICIPANT_ID));
                        kitRequestShipping.setBspCollaboratorSampleId(rs.getString(DBConstants.BSP_COLLABORATOR_SAMPLE_ID));
                        kitRequestShipping.setDdpParticipantId(rs.getString(DBConstants.DDP_PARTICIPANT_ID));
                        kitRequestShipping.setDdpLabel(rs.getString(DBConstants.DSM_LABEL));
                        kitRequestShipping.setCreatedBy(rs.getString(DBConstants.CREATED_BY));
                        kitRequestShipping.setCreatedDate(rs.getLong(DBConstants.CREATED_DATE));
                        kitRequestShipping.setExternalOrderNumber(rs.getString(DBConstants.EXTERNAL_ORDER_NUMBER));
                        kitRequestShipping.setExternalOrderDate(rs.getLong(DBConstants.EXTERNAL_ORDER_DATE));
                        kitRequestShipping.setExternalOrderStatus(rs.getString(DBConstants.EXTERNAL_ORDER_STATUS));
                        kitRequestShipping.setUploadReason(rs.getString(DBConstants.UPLOAD_REASON));
                        kitRequestShipping.setRequiresInsertInKitTracking(rs.getBoolean(DBConstants.REQUIRES_INSERT_KIT_TRACKING));
                        kitRequestShipping.setTrackingId(rs.getString(DBConstants.TRACKING_ID));
                        kitRequestShipping.setKitLabel(rs.getString(DBConstants.KIT_LABEL));
                        kitRequestShipping.setKitLabelPrefix(rs.getString(DBConstants.KIT_LABEL_PREFIX));
                        kitRequestShipping.setKitLabelLength(rs.getLong(DBConstants.KIT_LABEL_LENGTH));
                        kitRequestShipping.setDsmKitId(rs.getLong(DBConstants.DSM_KIT_ID));
                        kitRequestShipping.setMessage(rs.getString(DBConstants.MESSAGE));
                        dbVals.resultValue = kitRequestShipping;
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            if (dbVals.resultException != null) {
                throw new RuntimeException("Error getting kit request with ddp label " + ddpLabel, dbVals.resultException);
            }
            return dbVals;
        });
        return Optional.ofNullable((KitRequestShipping) results.resultValue);
    }

    @Override
    public Optional<List<KitRequestShipping>> getSubkitsByDdpLabel(String ddpLabel, String kitLabel) {
        List<KitRequestShipping> subkits = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            String subKitDDpLabel = ddpLabel.concat("\\_%");
            try (PreparedStatement stmt = conn.prepareStatement(SQL_GET_SUB_KIT_BY_DDP_LABEL)) {
                stmt.setString(1, kitLabel);
                stmt.setString(2, ddpLabel);
                stmt.setString(3, subKitDDpLabel);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        KitRequestShipping kitRequestShipping = new KitRequestShipping();
                        kitRequestShipping.setDsmKitRequestId(rs.getLong(DBConstants.DSM_KIT_REQUEST_ID));
                        kitRequestShipping.setDdpInstanceId(rs.getLong(DBConstants.DDP_INSTANCE_ID));
                        kitRequestShipping.setDdpKitRequestId(rs.getString(DBConstants.DDP_KIT_REQUEST_ID));
                        kitRequestShipping.setKitTypeId(String.valueOf(rs.getInt(DBConstants.KIT_TYPE_ID)));
                        kitRequestShipping.setBspCollaboratorParticipantId(rs.getString(DBConstants.COLLABORATOR_PARTICIPANT_ID));
                        kitRequestShipping.setBspCollaboratorSampleId(rs.getString(DBConstants.BSP_COLLABORATOR_SAMPLE_ID));
                        kitRequestShipping.setDdpParticipantId(rs.getString(DBConstants.DDP_PARTICIPANT_ID));
                        kitRequestShipping.setDdpLabel(rs.getString(DBConstants.DSM_LABEL));
                        kitRequestShipping.setCreatedBy(rs.getString(DBConstants.CREATED_BY));
                        kitRequestShipping.setCreatedDate(rs.getLong(DBConstants.CREATED_DATE));
                        kitRequestShipping.setExternalOrderNumber(rs.getString(DBConstants.EXTERNAL_ORDER_NUMBER));
                        kitRequestShipping.setExternalOrderDate(rs.getLong(DBConstants.EXTERNAL_ORDER_DATE));
                        kitRequestShipping.setExternalOrderStatus(rs.getString(DBConstants.EXTERNAL_ORDER_STATUS));
                        kitRequestShipping.setUploadReason(rs.getString(DBConstants.UPLOAD_REASON));
                        kitRequestShipping.setRequiresInsertInKitTracking(rs.getBoolean(DBConstants.REQUIRES_INSERT_KIT_TRACKING));
                        kitRequestShipping.setTrackingId(rs.getString(DBConstants.TRACKING_ID));
                        kitRequestShipping.setKitLabel(rs.getString(DBConstants.KIT_LABEL));
                        kitRequestShipping.setKitLabelPrefix(rs.getString(DBConstants.KIT_LABEL_PREFIX));
                        kitRequestShipping.setKitLabelLength(rs.getLong(DBConstants.KIT_LABEL_LENGTH));
                        kitRequestShipping.setDsmKitId(rs.getLong(DBConstants.DSM_KIT_ID));
                        kitRequestShipping.setKitTypeName(rs.getString(DBConstants.KIT_TYPE_NAME));
                        subkits.add(kitRequestShipping);
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            if (dbVals.resultException != null) {
                throw new RuntimeException("Error getting kit request with ddp label " + ddpLabel, dbVals.resultException);
            }
            logger.info(String.format("Found %d subkits with ddp Label %s", subkits.size(), ddpLabel));
            return dbVals;
        });
        return Optional.ofNullable((subkits));
    }

    public KitRequestShipping getKitRequestShipping(@NonNull ResultSet rs) throws SQLException {
        String returnTrackingId = rs.getString(DBConstants.TRACKING_ID);
        if (StringUtils.isBlank(returnTrackingId)) {
            returnTrackingId = rs.getString(DBConstants.DSM_TRACKING_RETURN);
        }
        KitRequestShipping kitRequestShipping =
                new KitRequestShipping(rs.getString(DBConstants.DDP_PARTICIPANT_ID), rs.getString(DBConstants.COLLABORATOR_PARTICIPANT_ID),
                        rs.getString(DBConstants.BSP_COLLABORATOR_SAMPLE_ID), rs.getString(DBConstants.DSM_LABEL),
                        rs.getString(DBConstants.INSTANCE_NAME), rs.getString(DBConstants.KIT_TYPE_NAME),
                        rs.getLong(DBConstants.DSM_KIT_REQUEST_ID), rs.getLong(DBConstants.DSM_KIT_ID),
                        rs.getString(DBConstants.DSM_LABEL_TO), rs.getString(DBConstants.DSM_LABEL_RETURN),
                        rs.getString(DBConstants.DSM_TRACKING_TO), returnTrackingId, rs.getString(DBConstants.DSM_TRACKING_URL_TO),
                        rs.getString(DBConstants.DSM_TRACKING_URL_RETURN), (Long) rs.getObject(DBConstants.DSM_SCAN_DATE),
                        rs.getBoolean(DBConstants.ERROR), rs.getString(DBConstants.MESSAGE),
                        (Long) rs.getObject(DBConstants.DSM_RECEIVE_DATE),
                        rs.getString(DBConstants.EASYPOST_ADDRESS_ID_TO), (Long) rs.getObject(DBConstants.DSM_DEACTIVATED_DATE),
                        rs.getString(DBConstants.DEACTIVATION_REASON), rs.getString(DBConstants.KIT_LABEL),
                        rs.getBoolean(DBConstants.EXPRESS), rs.getString(DBConstants.EASYPOST_TO_ID),
                        (Long) rs.getObject(DBConstants.LABEL_TRIGGERED_DATE), rs.getString(DBConstants.EASYPOST_SHIPMENT_STATUS),
                        rs.getString(DBConstants.EXTERNAL_ORDER_NUMBER), rs.getBoolean(DBConstants.NO_RETURN),
                        rs.getString(DBConstants.EXTERNAL_ORDER_STATUS), rs.getString(DBConstants.CREATED_BY),
                        rs.getString(DBConstants.KIT_TEST_RESULT), rs.getString(DBConstants.UPS_TRACKING_STATUS),
                        rs.getString(DBConstants.UPS_RETURN_STATUS),
                        (Long) rs.getObject(DBConstants.EXTERNAL_ORDER_DATE),
                        rs.getBoolean(DBConstants.CARE_EVOLVE), rs.getString(DBConstants.UPLOAD_REASON), null, null, null, null, null,
                        null, null, rs.getString(DBConstants.KIT_LABEL_PREFIX), rs.getLong(DBConstants.KIT_LABEL_LENGTH));
        if (DBUtil.columnExists(rs, DBConstants.UPS_STATUS_DESCRIPTION) && StringUtils.isNotBlank(
                rs.getString(DBConstants.UPS_STATUS_DESCRIPTION))) {
            String upsPackageTrackingNumber = rs.getString(DBConstants.UPS_PACKAGE_TABLE_ABBR + DBConstants.UPS_TRACKING_NUMBER);
            if (StringUtils.isNotBlank(upsPackageTrackingNumber) && upsPackageTrackingNumber.equals(kitRequestShipping.getTrackingToId())) {
                kitRequestShipping.setUpsTrackingStatus(rs.getString(DBConstants.UPS_STATUS_DESCRIPTION));

            } else if (StringUtils.isNotBlank(upsPackageTrackingNumber) && upsPackageTrackingNumber.equals(
                    kitRequestShipping.getTrackingReturnId())) {
                kitRequestShipping.setUpsReturnStatus(rs.getString(DBConstants.UPS_STATUS_DESCRIPTION));
            }

        }
        return kitRequestShipping;
    }

    @Override
    public Optional<ScanError> insertKitTracking(KitRequestShipping kitRequestShipping, String userId) {
        Optional<ScanError> result = Optional.empty();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(INSERT_KIT_TRACKING)) {
                stmt.setLong(1, System.currentTimeMillis());
                stmt.setString(2, userId);
                stmt.setString(3, kitRequestShipping.getTrackingId());
                stmt.setString(4, kitRequestShipping.getKitLabel());
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected != 1) {
                    dbVals.resultValue = new ScanError(kitRequestShipping.getKitLabel(),
                            "Kit Label \"" + kitRequestShipping.getKitLabel() + "\" does not exist.\n"
                                    + UserErrorMessages.IF_QUESTIONS_CONTACT_DEVELOPER);
                    logger.error("The number of affected rows for kit label " + kitRequestShipping.getKitLabel() + "is not 1.");
                } else {
                    logger.info("Added tracking for kit w/ kit_label " + kitRequestShipping.getKitLabel());
                }
            } catch (Exception ex) {
                dbVals.resultValue = new ScanError(kitRequestShipping.getKitLabel(),
                        "Kit Label \"" + kitRequestShipping.getKitLabel() + "\" does not exist.\n"
                                + UserErrorMessages.IF_QUESTIONS_CONTACT_DEVELOPER);
                logger.error("Unable to save kit tracking information for kit label " + kitRequestShipping.getKitLabel(), ex);
            }
            return dbVals;
        });
        if (Objects.nonNull(results.resultValue)) {
            result = Optional.ofNullable((ScanError) results.resultValue);
        }
        return result;
    }

    private boolean booleanCheckFoundAsName(String kitLabel, String query) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult(0);
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, kitLabel);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = rs.getInt(DBConstants.FOUND);
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            if (dbVals.resultException != null) {
                throw new RuntimeException("Error checking if kit exists in tracking table ", dbVals.resultException);
            }
            if (dbVals.resultValue == null) {
                throw new RuntimeException("Error checking if kit exists in tracking table ");
            }
            logger.info("Found " + dbVals.resultValue + " kit in tracking table w/ kit_label " + kitLabel);
            return dbVals;
        });

        if (results.resultException != null) {
            logger.error("Error checking if kit exists in tracking table w/ kit_label " + kitLabel, results.resultException);
        }
        return (int) results.resultValue > 0;
    }

    @Override
    public List<KitRequestShipping> getKitsByHruid(String hruid) {
        List<KitRequestShipping> kitRequestList = new ArrayList<>();
        inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_KIT_REQUEST + KIT_BY_HRUID)) {
                stmt.setString(1, "%" + hruid + "%");
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        kitRequestList.add(getKitRequestShipping(rs));
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            if (dbVals.resultException != null) {
                throw new RuntimeException("Error getting kit request with shortID " + hruid, dbVals.resultException);
            }
            return dbVals;
        });
        return kitRequestList;
    }

    @Override
    public Optional<ScanError> updateKitLabel(KitRequestShipping kitRequestShipping) {
        Optional<ScanError> result = Optional.empty();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(UPDATE_KIT_LABEL)) {
                stmt.setString(1, kitRequestShipping.getKitLabel());
                stmt.setLong(2, kitRequestShipping.getDsmKitId());
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected != 1) {
                    dbVals.resultValue = new ScanError(kitRequestShipping.getDdpLabel(), "dsm_kit_id "
                            + kitRequestShipping.getDsmKitId() + " does not exist or already has a Kit Label");
                } else {
                    logger.info("Updated kitRequests for pt w/ shortId " + kitRequestShipping.getHruid());
                }
            } catch (Exception ex) {
                dbVals.resultValue = new ScanError(kitRequestShipping.getDdpLabel(),
                        "Kit Label \"" + kitRequestShipping.getKitLabel() + "\" was already scanned.\n"
                                + UserErrorMessages.IF_QUESTIONS_CONTACT_DEVELOPER);
            }
            return dbVals;
        });
        if (Objects.nonNull(results.resultValue)) {
            result = Optional.ofNullable((ScanError) results.resultValue);
        }
        return result;
    }

    public boolean hasKitReceived(Connection connection, String ddpParticipantId) {
        try (PreparedStatement stmt = connection.prepareStatement(SQL_SELECT_RECEIVED_KITS)) {
            stmt.setString(1, ddpParticipantId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return true;
            }
        } catch (Exception ex) {
            throw new RuntimeException(String.format("Error getting kits for %s", ddpParticipantId));
        }
        return false;
    }

    public ResultSet getKitsInDatabaseByInstanceId(DDPInstance ddpInstance) {
        SimpleResult simpleResult = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SELECT_KIT_STATUS.concat(BY_INSTANCE_ID))) {
                stmt.setString(1, ddpInstance.getDdpInstanceId());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    dbVals.resultValue = rs;
                }
            } catch (Exception ex) {
                dbVals.resultException = new Exception(String.format("Error getting kits for %s", ddpInstance.getDdpInstanceId()));
            }
            return dbVals;
        });
        if (simpleResult.resultException != null) {
            throw new DSMBadRequestException(simpleResult.resultException);
        }
        return (ResultSet) simpleResult.resultValue;

    }

    @Override
    public ResultSet getKitsByJuniperKitId(String juniperKitId) {
        SimpleResult simpleResult = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SELECT_KIT_STATUS.concat(BY_JUNIPER_KIT_ID))) {
                stmt.setString(1, juniperKitId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    dbVals.resultValue = rs;
                }
            } catch (Exception ex) {
                dbVals.resultException = new Exception(String.format("Error getting kits with juniper kit id %s", juniperKitId));
            }
            return dbVals;
        });
        if (simpleResult.resultException != null) {
            throw new DSMBadRequestException(simpleResult.resultException);
        }
        return (ResultSet) simpleResult.resultValue;
    }

    @Override
    public ResultSet getKitsByParticipantId(String participantId) {
        SimpleResult simpleResult = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SELECT_KIT_STATUS.concat(BY_PARTICIPANT_ID))) {
                stmt.setString(1, participantId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    dbVals.resultValue = rs;
                }
            } catch (Exception ex) {
                dbVals.resultException = new Exception(String.format("Error getting kits with participant id %s", participantId));
            }
            return dbVals;
        });
        if (simpleResult.resultException != null) {
            throw new DSMBadRequestException(simpleResult.resultException);
        }
        return (ResultSet) simpleResult.resultValue;
    }
}
