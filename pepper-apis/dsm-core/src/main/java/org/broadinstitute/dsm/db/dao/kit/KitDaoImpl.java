package org.broadinstitute.dsm.db.dao.kit;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.route.KitStatusChangeRoute;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.KitUtil;
import org.broadinstitute.lddp.db.SimpleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KitDaoImpl implements KitDao {

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

    private static final String INSERT_KIT_TRACKING = "INSERT INTO "
            + "ddp_kit_tracking "
            + "SET "
            + "scan_date = ?, scan_by = ?, tracking_id = ?, kit_label = ?";

    private static final String INSERT_KIT = "INSERT INTO "
            + "ddp_kit "
            + "(dsm_kit_request_id, "
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
            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";

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
    public Boolean isBloodKit(String kitLabel) {
        return booleanCheckFoundAsName(kitLabel, SQL_IS_BLOOD_KIT_QUERY);
    }


    @Override
    public Boolean hasTrackingScan(String kitLabel) {
        return booleanCheckFoundAsName(kitLabel, SQL_HAS_KIT_TRACKING);
    }

    @Override
    public Optional<KitStatusChangeRoute.ScanError> updateKitRequest(KitRequestShipping kitRequestShipping, String userId) {
        Optional<KitStatusChangeRoute.ScanError> result = Optional.empty();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(UPDATE_KIT_REQUEST)) {
                stmt.setLong(1, System.currentTimeMillis());
                stmt.setString(2, userId);
                stmt.setString(3, kitRequestShipping.getKitLabel());
                stmt.setString(4, kitRequestShipping.getDdpLabel());
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected != 1) {
                    dbVals.resultValue = new KitStatusChangeRoute.ScanError(kitRequestShipping.getDdpLabel(), "ddp_label "
                            + kitRequestShipping.getDdpLabel() + " does not exist or already has a Kit Label");
                } else {
                    logger.info("Updated kitRequests w/ ddp_label " + kitRequestShipping.getDdpLabel());
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
            return dbVals;
        });
        if (Objects.nonNull(results.resultValue)) {
            result = (Optional<KitStatusChangeRoute.ScanError>) results.resultValue;
        }
        return result;
    }

    @Override
    public Optional<KitStatusChangeRoute.ScanError> updateKitReceived(KitRequestShipping kitRequestShipping,
                                                                      String userId) {
        Optional<KitStatusChangeRoute.ScanError> result = Optional.empty();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(UPDATE_KIT_RECEIVED)) {
                stmt.setLong(1, System.currentTimeMillis());
                stmt.setString(2, userId);
                stmt.setString(3, kitRequestShipping.getKitLabel());
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected != 1) {
                    //try to receive it as AT kit
                    dbVals.resultValue = Optional.of(new KitStatusChangeRoute.ScanError(kitRequestShipping.getKitLabel(),
                            "SM-ID \"" + kitRequestShipping.getKitLabel() + "\" does not exist or was already scanned as received.\n"
                                    + UserErrorMessages.IF_QUESTIONS_CONTACT_DEVELOPER));
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
            return dbVals;
        });
        if (Objects.nonNull(results.resultValue)) {
            result = (Optional<KitStatusChangeRoute.ScanError>) results.resultValue;
        }
        return result;
    }

    @Override
    public Integer insertKit(KitRequestShipping kitRequestShipping) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(INSERT_KIT)) {
                stmt.setLong(1, kitRequestShipping.getDsmKitRequestId());
                stmt.setString(2, kitRequestShipping.getLabelUrlTo());
                stmt.setString(3, kitRequestShipping.getLabelUrlReturn());
                stmt.setString(4, kitRequestShipping.getEasypostToId());
                stmt.setString(5, kitRequestShipping.getEasypostShipmentStatus());
                stmt.setString(6, kitRequestShipping.getTrackingToId());
                stmt.setString(7, kitRequestShipping.getTrackingReturnId());
                stmt.setString(8, kitRequestShipping.getEasypostTrackingToUrl());
                stmt.setString(9, kitRequestShipping.getEasypostTrackingReturnUrl());
                stmt.setBoolean(10, kitRequestShipping.getError());
                stmt.setString(11, kitRequestShipping.getMessage());
                stmt.setString(12, kitRequestShipping.getEasypostAddressId());
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
            try (PreparedStatement stmt = conn.prepareStatement(INSERT_KIT_REQUEST)) {
                stmt.setLong(1, kitRequestShipping.getDdpInstanceId());
                stmt.setLong(2, kitRequestShipping.getDdpKitRequestId());
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
            throw new RuntimeException("Error inserting kit with dsm_kit_request_id: "
                    + kitRequestShipping.getDsmKitRequestId(), results.resultException);
        }
        return (int) results.resultValue;
    }

    @Override
    public Optional<KitStatusChangeRoute.ScanError> insertKitTracking(KitRequestShipping kitRequestShipping, String userId) {
        Optional<KitStatusChangeRoute.ScanError> result = Optional.empty();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(INSERT_KIT_TRACKING)) {
                stmt.setLong(1, System.currentTimeMillis());
                stmt.setString(2, userId);
                stmt.setString(3, kitRequestShipping.getTrackingId());
                stmt.setString(4, kitRequestShipping.getKitLabel());
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected != 1) {
                    dbVals.resultValue = new KitStatusChangeRoute.ScanError(kitRequestShipping.getKitLabel(),
                            "Kit Label \"" + kitRequestShipping.getKitLabel() + "\" does not exist.\n"
                                    + UserErrorMessages.IF_QUESTIONS_CONTACT_DEVELOPER);
                } else {
                    logger.info("Added tracking for kit w/ kit_label " + kitRequestShipping.getKitLabel());
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
            return dbVals;
        });
        if (Objects.nonNull(results.resultValue)) {
            result = (Optional<KitStatusChangeRoute.ScanError>) results.resultValue;
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



}
