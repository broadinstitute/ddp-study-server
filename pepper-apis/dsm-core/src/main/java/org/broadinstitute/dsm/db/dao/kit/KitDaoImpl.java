package org.broadinstitute.dsm.db.dao.kit;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.io.IOException;
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
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.exception.DsmInternalError;
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
            "SELECT * FROM ( SELECT req.upload_reason, kt.kit_type_name, kt.display_name, ddp_site.instance_name, ddp_site.ddp_instance_id, "
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

    // update various ddp_kit scan fields if they have not been set already.  If
    // they have already been set, do nothing.
    private static final String SET_DDP_KIT_SCAN_INFO_BY_DDP_LABEL_IF_NOT_SET_ALREADY = "UPDATE ddp_kit SET "
            + "kit_complete = 1, scan_date = ?, scan_by = ?, kit_label = ? "
            + "WHERE "
            + "dsm_kit_request_id = (SELECT dsm_kit_request_id FROM ddp_kit_request WHERE ddp_label = ?) "
            + "AND not kit_complete <=> 1 "
            + "AND deactivated_date is null "
            + "AND not exists (select 1 from ddp_kit where kit_label = ?)";

    private static final String UPDATE_KIT_LABEL = "UPDATE ddp_kit SET "
            + "kit_label = ? "
            + "WHERE "
            + "dsm_kit_id = ?";

    private static final String INSERT_KIT_TRACKING = "INSERT INTO "
            + "ddp_kit_tracking(scan_date, scan_by, tracking_id, kit_label) "
            + "(select ?, ?, ?, ? from dual where not exists (select 1 from ddp_kit_tracking where (tracking_id = ? or kit_label = ?)))";

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

    private static final String INSERT_KIT = "INSERT INTO ddp_kit "
            + "(dsm_kit_request_id, kit_label, label_url_to, label_url_return, easypost_to_id, easypost_return_id, tracking_to_id, "
            + "tracking_return_id, easypost_tracking_to_url, easypost_tracking_return_url, error, message, easypost_address_id_to) "
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

    private static final String SQL_SELECT_RECEIVED_KITS = " SELECT receive_date FROM ddp_kit k LEFT JOIN ddp_kit_request r "
            + " ON (k.dsm_kit_request_id  = r.dsm_kit_request_id) WHERE ddp_participant_id = ? AND receive_date IS NOT NULL ";

    private static final String SQL_DELETE_KIT_REQUEST = "DELETE FROM ddp_kit_request WHERE dsm_kit_request_id = ?";

    private static final String SQL_DELETE_KIT = "DELETE FROM ddp_kit WHERE dsm_kit_id = ?";

    private static final String SQL_DELETE_KIT_TRACKING = "DELETE FROM ddp_kit_tracking WHERE kit_label = ?";

    private static final String UPDATE_KIT_RECEIVED = KitUtil.SQL_UPDATE_KIT_RECEIVED;

    /**
     * Returns at most one ddp_kit_tracking row that has the given kit_label or tracking_id
     */
    private static final String SELECT_KIT_TRACKING_BY_KIT_LABEL_OR_TRACKING_ID =
            "select t.kit_label,\n" +
            "       t.tracking_id,\n" +
            "       (select email from access_user where user_id = t.scan_by) as scan_by,\n" +
            "       case when t.scan_date is not null\n" +
            "            then from_unixtime(t.scan_date/1000)\n" +
            "            else null\n" +
            "       end as scan_date\n" +
            "from ddp_kit_tracking t\n" +
            "        where t.kit_label = ? or t.tracking_id = ? limit 1";

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

            // todo arz test that attempts to update kit to break uk to reproduce
            try (PreparedStatement stmt = conn.prepareStatement(SET_DDP_KIT_SCAN_INFO_BY_DDP_LABEL_IF_NOT_SET_ALREADY)) {
                stmt.setLong(1, System.currentTimeMillis());
                stmt.setString(2, userId);
                stmt.setString(3, kitRequestShipping.getKitLabel());
                stmt.setString(4, kitRequestShipping.getDdpLabel());
                stmt.setString(5, kitRequestShipping.getKitLabel());
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected != 1) {
                    // todo arz PR comment: with the new insert query, instead of a key violation,
                    // rowsAffected will be 0 and this error will be returned to the user
                    dbVals.resultValue = new ScanError(kitRequestShipping.getDdpLabel(), "ddp_label "
                            + kitRequestShipping.getDdpLabel() + " does not exist or already has a kit Label");
                } else {
                    logger.info("Updated ddp_kit.kit_label to {} for kit request  {}.",
                            kitRequestShipping.getKitLabel(), kitRequestShipping.getDdpLabel());
                    if (StringUtils.isNotBlank(kitRequestShipping.getBspCollaboratorParticipantId())) {
                        dbVals.resultValue = new ScanError(kitRequestShipping.getDdpLabel(), null,
                                kitRequestShipping.getBspCollaboratorParticipantId());
                    }
                }
            } catch (SQLException ex) {
                logger.error("Not able to update the kit for ddpLabel " + kitRequestShipping.getDdpLabel(), ex);
                dbVals.resultValue = new ScanError(kitRequestShipping.getDdpLabel(),
                        "An unexpected error has occurred");
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
    public Long insertKit(KitRequestShipping kitRequestShipping) {
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
                        dbVals.resultValue = rs.getLong(1);
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });
        if (Objects.nonNull(results.resultException)) {
            throw new DsmInternalError(String.format("Error inserting kit with dsm_kit_request_id: %s"
                    , kitRequestShipping.getDsmKitRequestId()) , results.resultException);
        }
        return (long) results.resultValue;
    }

    @Override
    public Long insertKitRequest(KitRequestShipping kitRequestShipping) {
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
                        dbVals.resultValue = rs.getLong(1);
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });
        if (Objects.nonNull(results.resultException)) {
            throw new DsmInternalError(String.format("Error inserting kit request for participant id: %s"
                    , kitRequestShipping.getDdpParticipantId()), results.resultException);
        }
        return (long) results.resultValue;
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
                        throw new DsmInternalError(String.format("Found %d kits for dsm_kit_request_id %s ", numRows, kitRequestId));
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new DsmInternalError(String.format("Error setting kitRequest to deactivated w/ dsm_kit_request_id %s" , kitRequestId),
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
                        throw new DsmInternalError(String.format("Found %d kits for dsm_kit_request_id %s ", numRows, kitId));
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new DsmInternalError(String.format("Error setting kitRequest to deactivated w/ dsm_kit_request_id %d", kitId),
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

    public Integer deleteKitTrackingByKitLabel(String kitLabel) {
        SimpleResult simpleResult = inTransaction(conn -> {
            SimpleResult execResult = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_DELETE_KIT_TRACKING)) {
                stmt.setString(1, kitLabel);
                execResult.resultValue = stmt.executeUpdate();
            } catch (SQLException sqle) {
                execResult.resultException = sqle;
            }
            return execResult;
        });

        if (simpleResult.resultException != null) {
            throw new RuntimeException("Error deleting kit tracking for kit " + kitLabel, simpleResult.resultException);
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
            throw new DsmInternalError(String.format("Error deleting kit with id: %d ", kitId), simpleResult.resultException);
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
                throw new DsmInternalError(String.format("Error getting kit request with ddp label %s", ddpLabel), dbVals.resultException);
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
                throw new DsmInternalError(String.format("Error getting kit request with ddp label %s", ddpLabel), dbVals.resultException);
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
                        null, null, rs.getString(DBConstants.KIT_LABEL_PREFIX), rs.getLong(DBConstants.KIT_LABEL_LENGTH),
                        rs.getString(DBConstants.KIT_DISPLAY_NAME));
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

    /**
     * Inserts a row into the kit tracking table as long as no row exists in the table
     * with the given kit label or tracking id.  If a row exists with either of these values,
     * no row is inserted and a scan error is returned with information about the existing row.
     */
    private Optional<ScanError> insertKitTrackingIfNotExists(KitRequestShipping kitRequestShipping, int userId) {
        Optional<ScanError> result = Optional.empty();
        String errorMessage = String.format("Unable to insert tracking %s for %s.", kitRequestShipping.getTrackingId(),
                kitRequestShipping.getKitLabel());
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SELECT_KIT_TRACKING_BY_KIT_LABEL_OR_TRACKING_ID)) {
                // If a kit exists with the given kit label or tracking id, the unique key will be violated
                // on insert.  Return an error to the user with some information about the existing row.
                stmt.setString(1, kitRequestShipping.getKitLabel());
                stmt.setString(2, kitRequestShipping.getTrackingId());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String kitLabel = rs.getString(DBConstants.KIT_LABEL);
                        String trackingId = rs.getString(DBConstants.TRACKING_ID);
                        String scannedAt = rs.getString(DBConstants.DSM_SCAN_DATE);
                        String scannedBy = rs.getString(DBConstants.SCAN_BY);
                        dbVals.resultValue = new ScanError(kitRequestShipping.getKitLabel(),
                                String.format("Kit % was already associated with tracking id %s by %s at %s",
                                        kitLabel, trackingId, scannedBy, scannedAt));
                        return dbVals;
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultValue = new ScanError(kitRequestShipping.getKitLabel(), errorMessage);
                logger.error(errorMessage, ex);
            }

            try (PreparedStatement stmt = conn.prepareStatement(INSERT_KIT_TRACKING)) {
                stmt.setLong(1, System.currentTimeMillis());
                stmt.setInt(2, userId);
                stmt.setString(3, kitRequestShipping.getTrackingId());
                stmt.setString(4, kitRequestShipping.getKitLabel());
                stmt.setString(5, kitRequestShipping.getTrackingId());
                stmt.setString(6, kitRequestShipping.getKitLabel());
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected != 1) {
                    // todo arz PR comment with insert query change, what was a unique key violation
                    // becomes 0 rows affected, triggering this error
                    dbVals.resultValue = new ScanError(kitRequestShipping.getKitLabel(), errorMessage);
                    logger.error(String.format(errorMessage + " %s rows affected", rowsAffected));
                } else {
                    logger.info("Added tracking id {} for kit {}", kitRequestShipping.getTrackingId(),
                            kitRequestShipping.getKitLabel());
                }
            } catch (SQLException ex) {
                dbVals.resultValue = new ScanError(kitRequestShipping.getKitLabel(), errorMessage);
                logger.error(errorMessage, ex);
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
                dbVals.resultException =  new DsmInternalError("Error checking if kit exists in tracking table ", dbVals.resultException);
            }
            if (dbVals.resultValue == null) {
                throw new DsmInternalError("Error checking if kit exists in tracking table ");
            }
            logger.info("Found {} kit in tracking table w/ kit_label {} ", dbVals.resultValue,  kitLabel);
            return dbVals;
        });

        if (results.resultException != null) {
            logger.error(String.format("Error checking if kit exists in tracking table w/ kit_label %s", kitLabel), results.resultException);
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
                throw new DsmInternalError(String.format("Error getting kit request with shortID %s", hruid), dbVals.resultException);
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
                    logger.info("Updated kitRequests for pt w/ shortId {}", kitRequestShipping.getHruid());
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

    @Override
    public Optional<ScanError> insertKitTrackingIfNotExists(String kitLabel, String trackingReturnId, int userId) {
        KitRequestShipping kitRequestShipping = new KitRequestShipping();
        kitRequestShipping.setTrackingId(trackingReturnId);
        kitRequestShipping.setKitLabel(kitLabel);
        return insertKitTrackingIfNotExists(kitRequestShipping, userId);
    }

    public boolean hasKitReceived(Connection connection, String ddpParticipantId) {
        try (PreparedStatement stmt = connection.prepareStatement(SQL_SELECT_RECEIVED_KITS)) {
            stmt.setString(1, ddpParticipantId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return true;
            }
        } catch (Exception ex) {
            throw new DsmInternalError(String.format("Error getting kits for %s", ddpParticipantId));
        }
        return false;
    }

}
