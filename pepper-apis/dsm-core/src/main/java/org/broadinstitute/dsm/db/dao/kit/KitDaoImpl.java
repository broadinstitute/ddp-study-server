package org.broadinstitute.dsm.db.dao.kit;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.model.KitDDPNotification;
import org.broadinstitute.dsm.model.elastic.export.painless.PutToNestedScriptBuilder;
import org.broadinstitute.dsm.model.elastic.export.painless.UpsertPainlessFacade;
import org.broadinstitute.dsm.route.KitStatusChangeRoute;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.DSMConfig;
import org.broadinstitute.dsm.util.EventUtil;
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
//                    logger.info("Updated kitRequests w/ ddp_label " + kitRequestShipping.getDdpLabel());
//                    KitDDPNotification kitDDPNotification = KitDDPNotification.getKitDDPNotification(
//                            DSMConfig.getSqlFromConfig(ApplicationConfigConstants.GET_SENT_KIT_INFORMATION_FOR_NOTIFICATION_EMAIL), kit,
//                            1);
//                    if (kitDDPNotification != null) {
//                        EventUtil.triggerDDP(conn, kitDDPNotification);
//                    }
//                    try {
//                        UpsertPainlessFacade.of(DBConstants.DDP_KIT_REQUEST_ALIAS, kitRequestShipping, ddpInstanceDto, "ddpLabel",
//                                "ddpLabel", kit, new PutToNestedScriptBuilder()).export();
//                    } catch (Exception e) {
//                        logger.error(String.format("Error updating ddp label for kit with label: %s", kit));
//                        e.printStackTrace();
//                    }

                    dbVals.resultValue = new KitStatusChangeRoute.ScanError(kitRequestShipping.getDdpLabel(), "ddp_label "
                            + kitRequestShipping.getDdpLabel() + " does not exist or already has a Kit Label");
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
