package org.broadinstitute.dsm.route;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import lombok.NonNull;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.handlers.util.Result;
import org.broadinstitute.dsm.model.KitDDPNotification;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.EventUtil;
import org.broadinstitute.dsm.util.KitUtil;
import org.broadinstitute.dsm.util.UserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

public class KitStatusChangeRoute extends RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(KitStatusChangeRoute.class);

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        if (UserUtil.checkUserAccess(null, userId, "kit_shipping") || UserUtil.checkUserAccess(null, userId, "kit_receiving")) {
            String requestBody = request.body();
            String userIdRequest = UserUtil.getUserId(request);
            if (!userId.equals(userIdRequest)) {
                throw new RuntimeException("User id was not equal. User Id in token " + userId + " user Id in request " + userIdRequest);
            }

            List<ScanError> scanErrorList = new ArrayList<>();

            long currentTime = System.currentTimeMillis();
            JsonArray scans = (JsonArray) (new JsonParser().parse(requestBody));
            int labelCount = scans.size();
            if (labelCount > 0) {
                if (request.url().endsWith(RoutePath.FINAL_SCAN_REQUEST)) {
                    updateKits(RoutePath.FINAL_SCAN_REQUEST, scans, currentTime, scanErrorList, userIdRequest);
                }
                else if (request.url().endsWith(RoutePath.TRACKING_SCAN_REQUEST)) {
                    updateKits(RoutePath.TRACKING_SCAN_REQUEST, scans, currentTime, scanErrorList, userIdRequest);
                }
                else if (request.url().endsWith(RoutePath.SENT_KIT_REQUEST)) {
                    updateKits(RoutePath.SENT_KIT_REQUEST, scans, currentTime, scanErrorList, userIdRequest);
                }
                else if (request.url().endsWith(RoutePath.RECEIVED_KIT_REQUEST)) {
                    updateKits(RoutePath.RECEIVED_KIT_REQUEST, scans, currentTime, scanErrorList, userIdRequest);
                }
                else {
                    logger.error("Endpoint was not known " + request.url());
                    return new Result(500, UserErrorMessages.CONTACT_DEVELOPER);
                }
            }
            return scanErrorList;
        }
        else {
            response.status(500);
            return new Result(500, UserErrorMessages.NO_RIGHTS);
        }
    }

    public void updateKits(@NonNull String changeType, @NonNull JsonArray scans, @NonNull long currentTime, @NonNull List<ScanError> scanErrorList, @NonNull String userId) {
        for (JsonElement scan : scans) {
            String addValue = null;
            String kit = null;
            if (RoutePath.FINAL_SCAN_REQUEST.equals(changeType)) {
                addValue = scan.getAsJsonObject().get("leftValue").getAsString();
                kit = scan.getAsJsonObject().get("rightValue").getAsString();
                //check if ddp_label is blood kit
                if (checkKitLabel(TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.GET_KIT_TYPE_NEED_TRACKING_BY_DDP_LABEL), kit)) {
                    //check if kit_label is in tracking table
                    if (checkKitLabel(TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.GET_FOUND_IF_KIT_LABEL_ALREADY_EXISTS_IN_TRACKING_TABLE), addValue)) {
                        updateKit(changeType, kit, addValue, currentTime, scanErrorList, userId);
                    }
                    else {
                        scanErrorList.add(new ScanError(kit, "Kit with DSM Label \"" + kit + "\" does not have a Tracking Label"));
                    }
                }
                else {
                    updateKit(changeType, kit, addValue, currentTime, scanErrorList, userId);
                }
            }
            else if (RoutePath.TRACKING_SCAN_REQUEST.equals(changeType)) {
                addValue = scan.getAsJsonObject().get("leftValue").getAsString();
                kit = scan.getAsJsonObject().get("rightValue").getAsString();
                updateKit(changeType, kit, addValue, currentTime, scanErrorList, userId);
            }
            else if (RoutePath.SENT_KIT_REQUEST.equals(changeType) || RoutePath.RECEIVED_KIT_REQUEST.equals(changeType)) {
                kit = scan.getAsJsonObject().get("kit").getAsString();
                updateKit(changeType, kit, addValue, currentTime, scanErrorList, userId);
            }
            else {
                throw new RuntimeException("Endpoint was not known");
            }
        }
    }

    private void updateKit(@NonNull String changeType, @NonNull String kit, String addValue, @NonNull long currentTime, @NonNull List<ScanError> scanErrorList, @NonNull String userId) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            String query = null;
            if (RoutePath.FINAL_SCAN_REQUEST.equals(changeType) || RoutePath.SENT_KIT_REQUEST.equals(changeType)) {
                query = TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.UPDATE_KIT_REQUEST);
            }
            else if (RoutePath.TRACKING_SCAN_REQUEST.equals(changeType)) {
                query = TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.INSERT_KIT_TRACKING);
            }
            else if (RoutePath.RECEIVED_KIT_REQUEST.equals(changeType)) {
                query = KitUtil.SQL_UPDATE_KIT_RECEIVED;
            }
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setLong(1, currentTime);
                stmt.setString(2, userId);
                if (RoutePath.RECEIVED_KIT_REQUEST.equals(changeType)) {
                    stmt.setString(3, kit);
                }
                else {
                    stmt.setString(3, addValue);
                    stmt.setString(4, kit);
                }
                int result = stmt.executeUpdate();
                if (result == 1) {
                    if (RoutePath.FINAL_SCAN_REQUEST.equals(changeType) || RoutePath.SENT_KIT_REQUEST.equals(changeType)) {
                        logger.info("Updated kitRequests w/ ddp_label " + kit);
                        KitDDPNotification kitDDPNotification = KitDDPNotification.getKitDDPNotification(TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.GET_SENT_KIT_INFORMATION_FOR_NOTIFICATION_EMAIL), kit, 1);
                        if (kitDDPNotification != null) {
                            EventUtil.triggerDDP(conn, kitDDPNotification);
                        }
                    }
                    else if (RoutePath.TRACKING_SCAN_REQUEST.equals(changeType)) {
                        logger.info("Added tracking for kit w/ kit_label " + kit);
                    }
                    else if (RoutePath.RECEIVED_KIT_REQUEST.equals(changeType)) {
                        logger.info("Updated kitRequest w/ SM-ID kit_label " + kit);
                    }
                }
                else {
                    if (RoutePath.FINAL_SCAN_REQUEST.equals(changeType) || RoutePath.SENT_KIT_REQUEST.equals(changeType)) {
                        scanErrorList.add(new ScanError(kit, "DSM Label \"" + kit + "\" does not exist or already has a Kit Label.\n" + UserErrorMessages.IF_QUESTIONS_CONTACT_DEVELOPER));
                        throw new RuntimeException("ddp_label " + kit + " does not exist or already has a Kit Label");
                    }
                    else if (RoutePath.TRACKING_SCAN_REQUEST.equals(changeType)) {
                        scanErrorList.add(new ScanError(kit, "Kit Label \"" + kit + "\" does not exist.\n" + UserErrorMessages.IF_QUESTIONS_CONTACT_DEVELOPER));
                        throw new RuntimeException("kit_label " + kit + " does not exist");
                    }
                    else if (RoutePath.RECEIVED_KIT_REQUEST.equals(changeType)) {
                        scanErrorList.add(new ScanError(kit, "SM-ID \"" + kit + "\" does not exist or was already scanned as received.\n" + UserErrorMessages.IF_QUESTIONS_CONTACT_DEVELOPER));
                        logger.warn("SM-ID kit_label " + kit + " does not exist or was already scanned as received");
                    }
                    else {
                        throw new RuntimeException("Error something went wrong at the scan pages");
                    }
                }
            }
            catch (Exception ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            if (RoutePath.FINAL_SCAN_REQUEST.equals(changeType) || RoutePath.SENT_KIT_REQUEST.equals(changeType)) {
                logger.error("Couldn't updated kitRequests w/ ddp_label " + kit, results.resultException);
                if (results.resultException.getClass() != RuntimeException.class) {
                    scanErrorList.add(new ScanError(kit, "Kit Label \"" + kit + "\" was already scanned.\n" + UserErrorMessages.IF_QUESTIONS_CONTACT_DEVELOPER));
                }
            }
            else if (RoutePath.TRACKING_SCAN_REQUEST.equals(changeType)) {
                logger.error("Couldn't insert tracking w/ kit_label " + kit, results.resultException);
                if (results.resultException.getClass() != RuntimeException.class) {
                    scanErrorList.add(new ScanError(kit, "Kit or Tracking Label were already scanned.\n" + UserErrorMessages.IF_QUESTIONS_CONTACT_DEVELOPER));
                }
            }
            else if (RoutePath.RECEIVED_KIT_REQUEST.equals(changeType)) {
                logger.warn("Couldn't updated kitRequests w/ SM-ID " + kit, results.resultException);
            }
            else {
                logger.error("Error something went wrong at the scan pages");
            }
        }
    }

    private boolean checkKitLabel(@NonNull String query, @NonNull String kitLabel) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult(0);
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, kitLabel);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = rs.getInt(DBConstants.FOUND);
                    }
                }
            }
            catch (SQLException ex) {
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
        boolean found = (int) results.resultValue > 0;
        return found;
    }

    public static class ScanError {
        private String kit;
        private String error;

        public ScanError(String kit, String error) {
            this.kit = kit;
            this.error = error;
        }
    }
}
