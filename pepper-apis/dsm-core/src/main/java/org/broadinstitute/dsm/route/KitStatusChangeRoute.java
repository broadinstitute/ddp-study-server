package org.broadinstitute.dsm.route;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import lombok.NonNull;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.handlers.util.Result;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.kitrequest.KitRequestDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.kitrequest.KitRequestDto;
import org.broadinstitute.dsm.model.KitDDPNotification;
import org.broadinstitute.dsm.model.KitRequestSettings;
import org.broadinstitute.dsm.model.at.ReceiveKitRequest;
import org.broadinstitute.dsm.model.elastic.export.painless.UpsertPainlessFacade;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.*;
import org.broadinstitute.dsm.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

public class KitStatusChangeRoute extends RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(KitStatusChangeRoute.class);

    private NotificationUtil notificationUtil;

    public KitStatusChangeRoute(@NonNull NotificationUtil notificationUtil) {
        this.notificationUtil = notificationUtil;
    }

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        String requestBody = request.body();
        String userIdRequest = UserUtil.getUserId(request);
        QueryParamsMap queryParams = request.queryMap();
        String realm = queryParams.get(RoutePath.REALM).value();
        DDPInstanceDto ddpInstanceDto = new DDPInstanceDao().getDDPInstanceByInstanceName(realm).orElseThrow();
        if (UserUtil.checkUserAccess(null, userId, "kit_shipping", userIdRequest) || UserUtil.checkUserAccess(null, userId, "kit_receiving", userIdRequest)) {
            List<ScanError> scanErrorList = new ArrayList<>();

            long currentTime = System.currentTimeMillis();
            JsonArray scans = (JsonArray) (new JsonParser().parse(requestBody));
            int labelCount = scans.size();
            if (labelCount > 0) {
                if (request.url().endsWith(RoutePath.FINAL_SCAN_REQUEST)) {
                    updateKits(RoutePath.FINAL_SCAN_REQUEST, scans, currentTime, scanErrorList, userIdRequest, ddpInstanceDto);
                }
                else if (request.url().endsWith(RoutePath.TRACKING_SCAN_REQUEST)) {
                    updateKits(RoutePath.TRACKING_SCAN_REQUEST, scans, currentTime, scanErrorList, userIdRequest, ddpInstanceDto);
                }
                else if (request.url().endsWith(RoutePath.SENT_KIT_REQUEST)) {
                    updateKits(RoutePath.SENT_KIT_REQUEST, scans, currentTime, scanErrorList, userIdRequest, ddpInstanceDto);
                }
                else if (request.url().endsWith(RoutePath.RECEIVED_KIT_REQUEST)) {
                    updateKits(RoutePath.RECEIVED_KIT_REQUEST, scans, currentTime, scanErrorList, userIdRequest, ddpInstanceDto);
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

    public void updateKits(@NonNull String changeType, @NonNull JsonArray scans, long currentTime, @NonNull List<ScanError> scanErrorList,
                           @NonNull String userId, DDPInstanceDto ddpInstanceDto) {
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
                        updateKit(changeType, kit, addValue, currentTime, scanErrorList, userId, ddpInstanceDto);
                        KitRequestDao kitRequestDao = new KitRequestDao();
                        KitRequestDto kitRequestByLabel = kitRequestDao.getKitRequestByLabel(kit);
                        if (kitRequestByLabel != null) {
                            writeSampleSentToES(kitRequestByLabel);
                        }
                    }
                    else {
                        scanErrorList.add(new ScanError(kit, "Kit with DSM Label \"" + kit + "\" does not have a Tracking Label"));
                    }
                }
                else {
                    updateKit(changeType, kit, addValue, currentTime, scanErrorList, userId, ddpInstanceDto);
                }
            }
            else if (RoutePath.TRACKING_SCAN_REQUEST.equals(changeType)) {
                addValue = scan.getAsJsonObject().get("leftValue").getAsString();
                kit = scan.getAsJsonObject().get("rightValue").getAsString();
                updateKit(changeType, kit, addValue, currentTime, scanErrorList, userId, ddpInstanceDto);
            }
            else if (RoutePath.SENT_KIT_REQUEST.equals(changeType) || RoutePath.RECEIVED_KIT_REQUEST.equals(changeType)) {
                kit = scan.getAsJsonObject().get("kit").getAsString();
                updateKit(changeType, kit, addValue, currentTime, scanErrorList, userId, ddpInstanceDto);
            }
            else {
                throw new RuntimeException("Endpoint was not known");
            }
        }
    }

    private static void writeSampleSentToES(KitRequestDto kitRequest) {
        int ddpInstanceId = kitRequest.getDdpInstanceId();
        DDPInstance ddpInstance = DDPInstance.getDDPInstanceById(ddpInstanceId);
        Map<String, Object> nameValuesMap = new HashMap<>();
        ElasticSearchDataUtil.setCurrentStrictYearMonthDay(nameValuesMap, ESObjectConstants.SENT);
        if (ddpInstance != null && kitRequest.getDdpKitRequestId() != null && kitRequest.getDdpParticipantId() != null) {
            ElasticSearchUtil.writeSample(
                    ddpInstance,
                    kitRequest.getDdpKitRequestId(),
                    kitRequest.getDdpParticipantId(),
                    ESObjectConstants.SAMPLES,
                    ESObjectConstants.KIT_REQUEST_ID, nameValuesMap
            );
        }
    }

    private void updateKit(@NonNull String changeType, @NonNull String kit, String addValue, long currentTime,
                           @NonNull List<ScanError> scanErrorList, @NonNull String userId,
                           DDPInstanceDto ddpInstanceDto) {
        KitRequestShipping kitRequestShipping = new KitRequestShipping();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            String query = null;
            if (RoutePath.FINAL_SCAN_REQUEST.equals(changeType) || RoutePath.SENT_KIT_REQUEST.equals(changeType)) {
                query = TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.UPDATE_KIT_REQUEST);
                kitRequestShipping.setScanDate(currentTime);
                kitRequestShipping.setKitLabel(addValue);
                kitRequestShipping.setDdpLabel(kit);
            }
            else if (RoutePath.TRACKING_SCAN_REQUEST.equals(changeType)) {
                query = TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.INSERT_KIT_TRACKING);
                //add value is value for tracking_id in ddp_kit_tracking and is considered as trackingReturnId in KitRequestShipping
                kitRequestShipping.setScanDate(currentTime);
                kitRequestShipping.setTrackingReturnId(addValue);
                kitRequestShipping.setKitLabel(kit);
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
                        UpsertPainlessFacade.of(DBConstants.DDP_KIT_REQUEST_ALIAS, kitRequestShipping, ddpInstanceDto, "ddpLabel", "ddpLabel", kit)
                                .export();
                    }
                    else if (RoutePath.TRACKING_SCAN_REQUEST.equals(changeType)) {
                        logger.info("Added tracking for kit w/ kit_label " + kit);
                        UpsertPainlessFacade.of(DBConstants.DDP_KIT_REQUEST_ALIAS, kitRequestShipping, ddpInstanceDto, "kitLabel", "kitLabel", addValue)
                                        .export();
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
                        //try to receive it as AT kit
                        if (!ReceiveKitRequest.receiveATKitRequest(notificationUtil, kit)) {
                            scanErrorList.add(new ScanError(kit, "SM-ID \"" + kit + "\" does not exist or was already scanned as received.\n" + UserErrorMessages.IF_QUESTIONS_CONTACT_DEVELOPER));
                            logger.warn("SM-ID kit_label " + kit + " does not exist or was already scanned as received");
                        }
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

    private String getKitRequestId(@NonNull String query, @NonNull String kitLabel) {
        List<String> ddpKitRequestIds = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult(0);
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, kitLabel);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        ddpKitRequestIds.add(rs.getString(0));
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            if (dbVals.resultException != null) {
                throw new RuntimeException("Error getting kit request id ", dbVals.resultException);
            }
            return dbVals;
        });
        return ddpKitRequestIds.get(0);
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
