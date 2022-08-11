package org.broadinstitute.dsm.route;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import lombok.NonNull;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.kitrequest.KitRequestDto;
import org.broadinstitute.dsm.model.KitDDPNotification;
import org.broadinstitute.dsm.model.at.ReceiveKitRequest;
import org.broadinstitute.dsm.model.elastic.export.painless.PutToNestedScriptBuilder;
import org.broadinstitute.dsm.model.elastic.export.painless.UpsertPainlessFacade;
import org.broadinstitute.dsm.model.kit.KitFinalScanUseCase;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.DSMConfig;
import org.broadinstitute.dsm.util.ElasticSearchDataUtil;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.EventUtil;
import org.broadinstitute.dsm.util.KitUtil;
import org.broadinstitute.dsm.util.NotificationUtil;
import org.broadinstitute.dsm.util.UserUtil;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
import org.broadinstitute.lddp.db.SimpleResult;
import org.broadinstitute.lddp.handlers.util.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

public abstract class KitStatusChangeRoute extends RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(KitStatusChangeRoute.class);

    protected NotificationUtil notificationUtil;
    protected KitPayload kitPayload;
    protected DDPInstanceDto ddpInstanceDto;
    protected String userIdRequest;
    protected List<ScanError> scanErrorList;
    protected List<ScanPayload> scanPayloads;
    protected JsonArray scans;
    protected long currentTime;

    public KitStatusChangeRoute(@NonNull NotificationUtil notificationUtil) {
        this.notificationUtil = notificationUtil;
    }

    public static void writeSampleSentToES(KitRequestDto kitRequest) {
        int ddpInstanceId = kitRequest.getDdpInstanceId();
        DDPInstance ddpInstance = DDPInstance.getDDPInstanceById(ddpInstanceId);
        Map<String, Object> nameValuesMap = new HashMap<>();
        ElasticSearchDataUtil.setCurrentStrictYearMonthDay(nameValuesMap, ESObjectConstants.SENT);
        if (ddpInstance != null && kitRequest.getDdpKitRequestId() != null && kitRequest.getDdpParticipantId() != null) {
            ElasticSearchUtil.writeSample(ddpInstance, kitRequest.getDdpKitRequestId(), kitRequest.getDdpParticipantId(),
                    ESObjectConstants.SAMPLES, ESObjectConstants.KIT_REQUEST_ID, nameValuesMap);
        }
    }

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        String requestBody = request.body();
        userIdRequest = UserUtil.getUserId(request);
        QueryParamsMap queryParams = request.queryMap();
        String realm = queryParams.get(RoutePath.REALM).value();
        ddpInstanceDto = new DDPInstanceDao().getDDPInstanceByInstanceName(realm).orElseThrow();
        if (UserUtil.checkUserAccess(null, userId, "kit_shipping", userIdRequest) || UserUtil.checkUserAccess(null, userId, "kit_receiving",
                userIdRequest)) {
            scanErrorList = new ArrayList<>();

            currentTime = System.currentTimeMillis();
            scans = (JsonArray) (new JsonParser().parse(requestBody));
            scanPayloads = ObjectMapperSingleton.readValue(requestBody, new TypeReference<List<ScanPayload>>() {});
            int labelCount = scans.size();
            if (labelCount > 0) {
                processRequest();
//                if (request.url().endsWith(RoutePath.FINAL_SCAN_REQUEST)) {
//                    updateKits(RoutePath.FINAL_SCAN_REQUEST, scans, currentTime, scanErrorList, userIdRequest, ddpInstanceDto);
//                } else if (request.url().endsWith(RoutePath.TRACKING_SCAN_REQUEST)) {
//                    updateKits(RoutePath.TRACKING_SCAN_REQUEST, scans, currentTime, scanErrorList, userIdRequest, ddpInstanceDto);
//                } else if (request.url().endsWith(RoutePath.SENT_KIT_REQUEST)) {
//                    updateKits(RoutePath.SENT_KIT_REQUEST, scans, currentTime, scanErrorList, userIdRequest, ddpInstanceDto);
//                } else if (request.url().endsWith(RoutePath.RECEIVED_KIT_REQUEST)) {
//                    updateKits(RoutePath.RECEIVED_KIT_REQUEST, scans, currentTime, scanErrorList, userIdRequest, ddpInstanceDto);
//                } else {
//                    logger.error("Endpoint was not known " + request.url());
//                    return new Result(500, UserErrorMessages.CONTACT_DEVELOPER);
//                }
            }
            return scanErrorList;
        } else {
            response.status(500);
            return new Result(500, UserErrorMessages.NO_RIGHTS);
        }
    }

    protected abstract void processRequest();

    public void updateKits(@NonNull String changeType, @NonNull JsonArray scans, long currentTime, @NonNull List<ScanError> scanErrorList,
                           @NonNull String userId, DDPInstanceDto ddpInstanceDto) {
        for (JsonElement scan : scans) {
            String addValue = null;
            String kit = null;
            if (RoutePath.FINAL_SCAN_REQUEST.equals(changeType)) {
                KitFinalScanUseCase.finalScanCommand(userId, ddpInstanceDto);
            } else if (RoutePath.TRACKING_SCAN_REQUEST.equals(changeType)) {
                trackingScanCommand(changeType, currentTime, scanErrorList, userId, ddpInstanceDto, scan);
            } else if (RoutePath.SENT_KIT_REQUEST.equals(changeType) || RoutePath.RECEIVED_KIT_REQUEST.equals(changeType)) {
                sentReceivedCommand(changeType, currentTime, scanErrorList, userId, ddpInstanceDto, scan, addValue);
            } else {
                throw new RuntimeException("Endpoint was not known");
            }
        }
    }

    private void sentReceivedCommand(String changeType, long currentTime, List<ScanError> scanErrorList, String userId, DDPInstanceDto ddpInstanceDto,
                           JsonElement scan, String addValue) {
        String kit;
        kit = scan.getAsJsonObject().get("kit").getAsString();
        updateKit(changeType, kit, addValue, currentTime, scanErrorList, userId, ddpInstanceDto);
    }

    private void trackingScanCommand(String changeType, long currentTime, List<ScanError> scanErrorList, String userId, DDPInstanceDto ddpInstanceDto,
                           JsonElement scan) {
        String kit;
        String addValue;
        addValue = scan.getAsJsonObject().get("leftValue").getAsString();
        kit = scan.getAsJsonObject().get("rightValue").getAsString();
        updateKit(changeType, kit, addValue, currentTime, scanErrorList, userId, ddpInstanceDto);
    }

    private void updateKit(@NonNull String changeType, @NonNull String kit, String addValue, long currentTime,
                           @NonNull List<ScanError> scanErrorList, @NonNull String userId, DDPInstanceDto ddpInstanceDto) {
        KitRequestShipping kitRequestShipping = new KitRequestShipping();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            String query = null;
            if (RoutePath.FINAL_SCAN_REQUEST.equals(changeType) || RoutePath.SENT_KIT_REQUEST.equals(changeType)) {
                query = DSMConfig.getSqlFromConfig(ApplicationConfigConstants.UPDATE_KIT_REQUEST);
                kitRequestShipping.setScanDate(currentTime);
                kitRequestShipping.setKitLabel(addValue);
                kitRequestShipping.setDdpLabel(kit);
            } else if (RoutePath.TRACKING_SCAN_REQUEST.equals(changeType)) {
                query = DSMConfig.getSqlFromConfig(ApplicationConfigConstants.INSERT_KIT_TRACKING);
                //add value is value for tracking_id in ddp_kit_tracking and is considered as trackingReturnId in KitRequestShipping
                kitRequestShipping.setScanDate(currentTime);
                kitRequestShipping.setTrackingReturnId(addValue);
                kitRequestShipping.setKitLabel(kit);
            } else if (RoutePath.RECEIVED_KIT_REQUEST.equals(changeType)) {
                query = KitUtil.SQL_UPDATE_KIT_RECEIVED;
            }
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setLong(1, currentTime);
                stmt.setString(2, userId);
                if (RoutePath.RECEIVED_KIT_REQUEST.equals(changeType)) {
                    stmt.setString(3, kit);
                } else {
                    stmt.setString(3, addValue);
                    stmt.setString(4, kit);
                }
                int result = stmt.executeUpdate();
                if (result == 1) {
                    if (RoutePath.FINAL_SCAN_REQUEST.equals(changeType) || RoutePath.SENT_KIT_REQUEST.equals(changeType)) {
                        logger.info("Updated kitRequests w/ ddp_label " + kit);
                        KitDDPNotification kitDDPNotification = KitDDPNotification.getKitDDPNotification(
                                DSMConfig.getSqlFromConfig(ApplicationConfigConstants.GET_SENT_KIT_INFORMATION_FOR_NOTIFICATION_EMAIL), kit,
                                1);
                        if (kitDDPNotification != null) {
                            EventUtil.triggerDDP(conn, kitDDPNotification);
                        }
                        try {
                            UpsertPainlessFacade.of(DBConstants.DDP_KIT_REQUEST_ALIAS, kitRequestShipping, ddpInstanceDto, "ddpLabel",
                                    "ddpLabel", kit, new PutToNestedScriptBuilder()).export();
                        } catch (Exception e) {
                            logger.error(String.format("Error updating ddp label for kit with label: %s", kit));
                            e.printStackTrace();
                        }
                    } else if (RoutePath.TRACKING_SCAN_REQUEST.equals(changeType)) {
                        logger.info("Added tracking for kit w/ kit_label " + kit);
                        try {
                            UpsertPainlessFacade.of(DBConstants.DDP_KIT_REQUEST_ALIAS, kitRequestShipping, ddpInstanceDto, "kitLabel",
                                    "kitLabel", addValue, new PutToNestedScriptBuilder()).export();
                        } catch (Exception e) {
                            logger.error(String.format("Error updating kit label for kit with label: %s", addValue));
                            e.printStackTrace();
                        }
                    } else if (RoutePath.RECEIVED_KIT_REQUEST.equals(changeType)) {
                        logger.info("Updated kitRequest w/ SM-ID kit_label " + kit);
                    }
                } else {
                    if (RoutePath.FINAL_SCAN_REQUEST.equals(changeType) || RoutePath.SENT_KIT_REQUEST.equals(changeType)) {
                        scanErrorList.add(new ScanError(kit, "DSM Label \"" + kit + "\" does not exist or already has a Kit Label.\n"
                                + UserErrorMessages.IF_QUESTIONS_CONTACT_DEVELOPER));
                        throw new RuntimeException("ddp_label " + kit + " does not exist or already has a Kit Label");
                    } else if (RoutePath.TRACKING_SCAN_REQUEST.equals(changeType)) {
                        scanErrorList.add(new ScanError(kit,
                                "Kit Label \"" + kit + "\" does not exist.\n" + UserErrorMessages.IF_QUESTIONS_CONTACT_DEVELOPER));
                        throw new RuntimeException("kit_label " + kit + " does not exist");
                    } else if (RoutePath.RECEIVED_KIT_REQUEST.equals(changeType)) {
                        //try to receive it as AT kit
                        if (!ReceiveKitRequest.receiveATKitRequest(notificationUtil, kit)) {
                            scanErrorList.add(new ScanError(kit,
                                    "SM-ID \"" + kit + "\" does not exist or was already scanned as received.\n"
                                            + UserErrorMessages.IF_QUESTIONS_CONTACT_DEVELOPER));
                            logger.warn("SM-ID kit_label " + kit + " does not exist or was already scanned as received");
                        }
                    } else {
                        throw new RuntimeException("Error something went wrong at the scan pages");
                    }
                }
            } catch (Exception ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            if (RoutePath.FINAL_SCAN_REQUEST.equals(changeType) || RoutePath.SENT_KIT_REQUEST.equals(changeType)) {
                logger.error("Couldn't updated kitRequests w/ ddp_label " + kit, results.resultException);
                if (results.resultException.getClass() != RuntimeException.class) {
                    scanErrorList.add(new ScanError(kit,
                            "Kit Label \"" + kit + "\" was already scanned.\n" + UserErrorMessages.IF_QUESTIONS_CONTACT_DEVELOPER));
                }
            } else if (RoutePath.TRACKING_SCAN_REQUEST.equals(changeType)) {
                logger.error("Couldn't insert tracking w/ kit_label " + kit, results.resultException);
                if (results.resultException.getClass() != RuntimeException.class) {
                    scanErrorList.add(new ScanError(kit,
                            "Kit or Tracking Label were already scanned.\n" + UserErrorMessages.IF_QUESTIONS_CONTACT_DEVELOPER));
                }
            } else if (RoutePath.RECEIVED_KIT_REQUEST.equals(changeType)) {
                logger.warn("Couldn't updated kitRequests w/ SM-ID " + kit, results.resultException);
            } else {
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
