package org.broadinstitute.dsm.jobs;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.DSMServer;
import org.broadinstitute.dsm.careevolve.Covid19OrderRegistrar;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.DdpKit;
import org.broadinstitute.dsm.model.KitDDPNotification;
import org.broadinstitute.dsm.model.ups.*;
import org.broadinstitute.dsm.shipping.UPSTracker;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.EventUtil;
import org.broadinstitute.dsm.util.KitUtil;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.*;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

public class TestBostonUPSTrackingJob implements Job {

    private static final Logger logger = LoggerFactory.getLogger(TestBostonUPSTrackingJob.class);
    private static final String SQL_SELECT_KITS = "SELECT kit.dsm_kit_request_id, kit.kit_label, kit.tracking_to_id, kit.tracking_return_id, kit.error, kit.message, kit.receive_date, kit.ups_tracking_status, kit.ups_tracking_date, kit.ups_return_date, kit.ups_return_status, req.bsp_collaborator_participant_id, " +
            " req.external_order_number, kit.CE_order FROM ddp_kit kit LEFT JOIN ddp_kit_request req " +
            " ON (kit.dsm_kit_request_id = req.dsm_kit_request_id) WHERE req.ddp_instance_id = ? and kit_label not like \"%\\\\_1\"  ";

    private static String SQL_AVOID_DELIVERED = "and (tracking_to_id is not null or tracking_return_id is not null ) and (ups_tracking_status is null or ups_return_status is null or ups_tracking_status not like \"" + UPSStatus.DELIVERED_TYPE + " %\" or ups_return_status not like \"" + UPSStatus.DELIVERED_TYPE + " %\")" +
            " and test_result is null"+
            " order by kit.dsm_kit_request_id ASC";

    private static final String SQL_UPDATE_UPS_TRACKING_STATUS = "UPDATE ddp_kit SET ups_tracking_status = ?, ups_tracking_date = ? " +
            "WHERE dsm_kit_id <> 0 and  tracking_to_id = ? and dsm_kit_request_id in ( SELECT dsm_kit_request_id FROM ddp_kit_request where external_order_number = ? )";
    private static final String SQL_UPDATE_UPS_RETURN_STATUS = "UPDATE ddp_kit SET ups_return_status = ?, ups_return_date = ? " +
            "WHERE dsm_kit_id <> 0 and tracking_return_id= ? and dsm_kit_request_id in ( SELECT dsm_kit_request_id FROM ddp_kit_request where external_order_number = ? )";

    private static final String SQL_SELECT_KIT_FOR_NOTIFICATION_EXTERNAL_SHIPPER = "select  eve.*,   request.ddp_participant_id,   request.ddp_label,   request.dsm_kit_request_id, request.ddp_kit_request_id, request.upload_reason, " +
            "        realm.ddp_instance_id, realm.instance_name, realm.base_url, realm.auth0_token, realm.notification_recipients, realm.migrated_ddp, kit.receive_date, kit.scan_date" +
            "        from ddp_kit_request request, ddp_kit kit, event_type eve, ddp_instance realm where request.dsm_kit_request_id = kit.dsm_kit_request_id and request.ddp_instance_id = realm.ddp_instance_id" +
            "        and not exists " +
            "                    (select 1 from EVENT_QUEUE q" +
            "                    where q.DDP_INSTANCE_ID = realm.ddp_instance_id" +
            "                    and " +
            "                    q.EVENT_TYPE = eve.event_name" +
            "                    and " +
            "                    q.DSM_KIT_REQUEST_ID = request.dsm_kit_request_id " +
            "                    and q.event_triggered = true" +
            "                    )"+
            "        and (eve.ddp_instance_id = request.ddp_instance_id and eve.kit_type_id = request.kit_type_id) and eve.event_type = ? "+
            "         and realm.ddp_instance_id = ?" +
            "          and kit.dsm_kit_request_id = ?";

    static String DELIVERED = "DELIVERED";
    static String RECEIVED = "RECEIVED";

        private static String SELECT_BY_EXTERNAL_ORDER_NUMBER = "and request.external_order_number = ?";
    private static Covid19OrderRegistrar orderRegistrar;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        logger.info("Starting the UPS lookup job");
        orderRegistrar = new Covid19OrderRegistrar(DSMServer.careEvolveOrderEndpoint, DSMServer.careEvolveAccount, DSMServer.provider,
                DSMServer.careEvolveMaxRetries, DSMServer.careEvolveRetyWaitSeconds);
        List<DDPInstance> ddpInstanceList = DDPInstance.getDDPInstanceListWithRole("ups_tracking");
        for (DDPInstance ddpInstance : ddpInstanceList) {
            if (ddpInstance != null && ddpInstance.isHasRole()) {
                logger.info("tracking ups ids for " + ddpInstance.getName());
                SimpleResult result = inTransaction((conn) -> {
                    SimpleResult dbVals = new SimpleResult();
                    String query = SQL_SELECT_KITS + SQL_AVOID_DELIVERED;
                    try (PreparedStatement stmt = conn.prepareStatement(query)) {
                        stmt.setString(1, ddpInstance.getDdpInstanceId());
                        try (ResultSet rs = stmt.executeQuery()) {
                            while (rs.next()) {
                                DdpKit kit = new DdpKit(
                                        rs.getString(DBConstants.DSM_KIT_REQUEST_ID),
                                        rs.getString("kit." + DBConstants.KIT_LABEL),
                                        rs.getString("kit." + DBConstants.DSM_TRACKING_TO),
                                        rs.getString("kit." + DBConstants.TRACKING_RETURN_ID),
                                        rs.getString("kit." + DBConstants.ERROR),
                                        rs.getString("kit." + DBConstants.MESSAGE),
                                        rs.getString("kit." + DBConstants.DSM_RECEIVE_DATE),
                                        rs.getString("kit." + DBConstants.UPS_TRACKING_STATUS),
                                        rs.getString("kit." + DBConstants.UPS_TRACKING_DATE),
                                        rs.getString("kit." + DBConstants.UPS_RETURN_STATUS),
                                        rs.getString("kit." + DBConstants.UPS_RETURN_DATE),
                                        rs.getString("req." + DBConstants.COLLABORATOR_PARTICIPANT_ID),
                                        rs.getString("req." + DBConstants.EXTERNAL_ORDER_NUMBER),
                                        rs.getBoolean("kit." + DBConstants.CE_ORDER)
                                );

                                if (StringUtils.isNotBlank(kit.getTrackingToId()) && !kit.isDelivered()) {
                                    try {
                                        updateKitStatus(conn, kit, false, ddpInstance);
                                    } catch (Exception e) {
                                        logger.error("Could not update outbound status for " + kit.getExternalOrderNumber() + " " + e.toString(), e);
                                    }
                                }

                                if (StringUtils.isNotBlank(kit.getTrackingReturnId()) && !kit.isReturned()) {
                                    try {
                                        updateKitStatus(conn, kit, true, ddpInstance);
                                    } catch (Exception e) {
                                        logger.error("Could not update return status for " + kit.getExternalOrderNumber() + " " + e.toString(), e);
                                    }
                                }
                            }
                        }
                    }
                    catch (Exception e) {
                        dbVals.resultException = e;
                    }
                    return dbVals;
                });
                if (result.resultException != null) {
                    throw new RuntimeException(result.resultException);
                }
            }
        }
    }

    public static UPSTrackingResponse lookupTrackingInfo(String trackingId) {
        return new UPSTracker(DSMServer.UPS_ENDPOINT, DSMServer.UPS_USERNAME, DSMServer.UPS_PASSWORD, DSMServer.UPS_ACCESSKEY).lookupTrackingInfo(trackingId);
    }

    public static void updateKitStatus(@NonNull Connection conn, DdpKit kit, boolean isReturn, DDPInstance ddpInstance) {
        String trackingId;
        if (!isReturn) {
            trackingId = kit.getTrackingToId();
        }
        else {
            trackingId = kit.getTrackingReturnId();
        }

        logger.info("Checking UPS status for " + trackingId + " for kit w/ external order number "+ kit.getExternalOrderNumber());
        UPSTrackingResponse response = lookupTrackingInfo(trackingId);
        logger.info("UPS response for " + trackingId + " is " + response);
        String type;
        if (isReturn) {
            type = kit.getUpsReturnStatus();
        }
        else {
            type = kit.getUpsTrackingStatus();
        }
        if (StringUtils.isNotBlank(type)) {// get only type from it
            type = type.substring(0, type.indexOf(' '));
        }
        if (response != null && response.getErrors() == null) {
            updateStatus(conn, trackingId, type, response, isReturn, kit, ddpInstance);
        }
        else {
            logError(trackingId, response.getErrors());
        }
    }

    private static void logError(String trackingId, UPSError[] errors) {
        String errorString = "";
        for (UPSError error : errors) {
            errorString += "Got Error: " + error.getCode() + " " + error.getMessage() + " For Tracking Number " + trackingId;
        }
        logger.error(errorString);
    }

    private static void updateStatus(@NonNull Connection conn, String trackingId, String oldType, UPSTrackingResponse response, boolean isReturn, DdpKit kit, DDPInstance ddpInstance) {
        if (response.getTrackResponse() != null) {
            UPSShipment[] shipment = response.getTrackResponse().getShipment();

            if (shipment != null && shipment.length > 0) {
                UPSPackage[] upsPackages = shipment[0].getUpsPackageArray();
                if (upsPackages != null && upsPackages.length > 0) {
                    UPSPackage upsPackage = upsPackages[0];
                    UPSActivity activity = upsPackage.getActivity()[0];

                    if (activity != null) {
                        UPSStatus status = activity.getStatus();
                        if (status != null) {
                            String statusType = status.getType();
                            String statusDescription = status.getDescription();
                            String date = activity.getDateTimeString();
                            String sqlUpdate = SQL_UPDATE_UPS_TRACKING_STATUS;

                            UPSActivity earliestPackageMovementEvent = upsPackage.getEarliestPackageMovementEvent();
                            Instant earliestPackageMovement = null;
                            if (earliestPackageMovementEvent != null) {
                                earliestPackageMovement = earliestPackageMovementEvent.getInstant();
                            }
                            if (isReturn) {
                                sqlUpdate = SQL_UPDATE_UPS_RETURN_STATUS;
                            }
                            updateTrackingInfo(conn, statusType, oldType, statusDescription, trackingId, date, sqlUpdate, isReturn, kit, earliestPackageMovement, ddpInstance);
                        }
                    }
                }
            }
        }
    }


    private static void updateTrackingInfo(@NonNull Connection conn, String statusType,
                                           String oldType,
                                           String statusDescription,
                                           String trackingId,
                                           String date,
                                           String query,
                                           boolean isReturn,
                                           DdpKit kit,
                                           Instant earliestInTransitTime,
                                           DDPInstance ddpInstance) {
        String upsUpdate = statusType + " " + statusDescription;
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, upsUpdate);
            stmt.setString(2, date);
            stmt.setString(3, trackingId);
            stmt.setString(4, kit.getExternalOrderNumber());
            int r = stmt.executeUpdate();
            if (r != 2) {//number of subkits
                logger.error("Update query for UPS tracking updated " + r + " rows! with tracking/return id: " + trackingId + " for kit w/ external order number "+ kit.getExternalOrderNumber());
            }

            if (!isReturn) {
                if (shouldTriggerEventForKitOnItsWayToParticipant(statusType, oldType)) {
                    KitDDPNotification kitDDPNotification = KitDDPNotification.getKitDDPNotification(conn,SQL_SELECT_KIT_FOR_NOTIFICATION_EXTERNAL_SHIPPER + SELECT_BY_EXTERNAL_ORDER_NUMBER, new String[] {  DELIVERED, ddpInstance.getDdpInstanceId(), kit.getDsmKitRequestId(), kit.getExternalOrderNumber() }, 1);//todo change this to the number of subkits but for now 2 for test boston works
                    if (kitDDPNotification != null) {
                        logger.info("Triggering DDP for kit going to participant with external order number: " + kit.getExternalOrderNumber());
                        EventUtil.triggerDDP(conn, kitDDPNotification);
                    }
                    else {
                        logger.error("delivered kitDDPNotification was null for " + kit.getExternalOrderNumber());
                    }
                }

            }
            else { // this is a return
                if (earliestInTransitTime != null && !kit.isCEOrdered()) {
                    // if we have the first date of an inbound event, create an order in CE
                    // using the earliest date of inbound event
                    orderRegistrar.orderTest(DSMServer.careEvolveAuth, kit.getHRUID(), kit.getMainKitLabel(), kit.getExternalOrderNumber(), earliestInTransitTime);
                    logger.info("Placed CE order for kit with external order number " + kit.getExternalOrderNumber());
                    kit.changeCEOrdered(conn,true);
                }
                else {
                    logger.info("No return events for " + kit.getMainKitLabel() + ".  Will not place order yet.");
                }
                if (shouldTriggerEventForReturnKitDelivery(statusType, oldType)) {
                    KitUtil.setKitReceived(conn, kit.getMainKitLabel());
                    logger.info("RECEIVED: " + trackingId);
                    KitDDPNotification kitDDPNotification = KitDDPNotification.getKitDDPNotification(conn,SQL_SELECT_KIT_FOR_NOTIFICATION_EXTERNAL_SHIPPER + SELECT_BY_EXTERNAL_ORDER_NUMBER, new String[] { RECEIVED,  ddpInstance.getDdpInstanceId(), kit.getDsmKitRequestId(), kit.getExternalOrderNumber() }, 1);//todo change this to the number of subkits but for now 2 for test boston works
                    if (kitDDPNotification != null) {
                        logger.info("Triggering DDP for received kit with external order number: " + kit.getExternalOrderNumber());
                        EventUtil.triggerDDP(conn, kitDDPNotification);

                    }
                    else {
                        logger.error("received kitDDPNotification was null for " + kit.getExternalOrderNumber());
                    }
                }
            }
            logger.info("Updated status of tracking number " + trackingId + " to " + upsUpdate + " from " + oldType+ " for kit w/ external order number "+ kit.getExternalOrderNumber());
        }
        catch (Exception e) {
            throw new RuntimeException("Could not update tracking info for tracking id " + trackingId, e);
        }
    }

    /**
     * Determines whether or not a trigger should be sent to
     * study-server to respond to kit being sent to participant
     */
    private static boolean shouldTriggerEventForKitOnItsWayToParticipant(String currentStatus, String previousStatus) {
        List<String> triggerStates = Arrays.asList(UPSStatus.DELIVERED_TYPE, UPSStatus.IN_TRANSIT_TYPE);
        return triggerStates.contains(currentStatus) && !triggerStates.contains(previousStatus);
    }

    /**
     * Determines whether or not a trigger should be sent to
     * study-server to respond to kit being delivered back at broad
     */
    private static boolean shouldTriggerEventForReturnKitDelivery(String currentStatus, String previousStatus) {
        List<String> triggerStates = Arrays.asList(UPSStatus.DELIVERED_TYPE);
        return triggerStates.contains(currentStatus) && !triggerStates.contains(previousStatus);
    }
}
