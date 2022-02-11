package org.broadinstitute.dsm.jobs;

import static org.broadinstitute.dsm.db.dao.ddp.kitrequest.KitRequestDao.BY_KIT_LABEL;
import static org.broadinstitute.dsm.db.dao.ddp.kitrequest.KitRequestDao.SQL_GET_KIT_REQUEST;

import com.google.cloud.functions.BackgroundFunction;
import com.google.cloud.functions.Context;
import com.google.gson.Gson;
import com.typesafe.config.Config;
import org.apache.commons.dbcp2.PoolableConnection;
import org.apache.commons.dbcp2.PoolingDataSource;
import org.apache.commons.lang3.tuple.Pair;
import org.broadinstitute.ddp.security.Auth0Util;
import org.broadinstitute.dsm.careevolve.Authentication;
import org.broadinstitute.dsm.careevolve.Covid19OrderRegistrar;
import org.broadinstitute.dsm.careevolve.Provider;
import org.broadinstitute.dsm.cf.CFUtil;
import org.broadinstitute.dsm.db.InstanceSettings;
import org.broadinstitute.dsm.db.dto.ddp.kitrequest.KitRequestDto;
import org.broadinstitute.dsm.model.KitDDPNotification;
import org.broadinstitute.dsm.model.ups.*;
import org.broadinstitute.dsm.shipping.UPSTracker;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.EventUtil;
import org.broadinstitute.dsm.util.KitUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.utils.StringUtils;

import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;


public class TestBostonUPSTrackingJob implements BackgroundFunction<PubsubMessage> {

    public static final String SKIP_CE_ORDERS_FOR_SCHEDULED_KITS_AFTER = "testboston.skipCEOrdersForScheduledKitsAfter";

    private String STUDY_MANAGER_SCHEMA = System.getenv("STUDY_MANAGER_SCHEMA") + ".";

    private final Logger logger = LoggerFactory.getLogger(TestBostonUPSTrackingJob.class);

    String DELIVERED = "DELIVERED";
    String RECEIVED = "RECEIVED";

    private String SELECT_BY_EXTERNAL_ORDER_NUMBER = "and request.external_order_number = ?";
    private Covid19OrderRegistrar orderRegistrar;
    private Authentication careEvolveAuth = null;
    String endpoint;
    String username;
    String password;
    String accessKey;
    Date ignoreScheduledOrdersAfter;
    Connection conn = null;
    private final int MAX_CONNECTION = 1;
    private final String SKIP_CE_ORDER_DATE_FORMAT = "YYYY-MM-dd";
    UPSTracker upsTracker = null;
    private static Auth0Util auth0Util;

    @Override
    public void accept(PubsubMessage message, Context context) throws Exception {
        if (message.data == null) {
            logger.error("No pubsub message provided");
            return;
        }
        Config cfg = CFUtil.loadConfig();
        String dbUrl = cfg.getString("dsmDBUrl");
        endpoint = cfg.getString("ups.url");
        username = cfg.getString("ups.username");
        password = cfg.getString("ups.password");
        accessKey = cfg.getString("ups.accesskey");
        if (cfg.hasPath(SKIP_CE_ORDERS_FOR_SCHEDULED_KITS_AFTER)) {
            String skipAfter = cfg.getString(SKIP_CE_ORDERS_FOR_SCHEDULED_KITS_AFTER);
            try {
                ignoreScheduledOrdersAfter = new SimpleDateFormat(SKIP_CE_ORDER_DATE_FORMAT).parse(skipAfter);
                logger.info("Will skip all CE orders after " + skipAfter);
            } catch (ParseException e) {
                logger.error("Could not parse cutoff time for testboston longitudinal orders.  Expected format is " + SKIP_CE_ORDER_DATE_FORMAT,e);
            }
        } else {
            logger.info("No cutoff date for CE orders of longitudinal kits.  Will continue to place CE orders");
        }
        upsTracker = new UPSTracker(endpoint, username, password, accessKey);
        logger.info("Starting the UPS lookup job");
        auth0Util = new Auth0Util(cfg.getString(ApplicationConfigConstants.AUTH0_ACCOUNT),
                cfg.getStringList(ApplicationConfigConstants.AUTH0_CONNECTIONS),
                cfg.getBoolean(ApplicationConfigConstants.AUTH0_IS_BASE_64_ENCODED),
                cfg.getString(ApplicationConfigConstants.AUTH0_CLIENT_KEY),
                cfg.getString(ApplicationConfigConstants.AUTH0_SECRET),
                cfg.getString(ApplicationConfigConstants.AUTH0_MGT_KEY),
                cfg.getString(ApplicationConfigConstants.AUTH0_MGT_SECRET),
                cfg.getString(ApplicationConfigConstants.AUTH0_MGT_API_URL),
                false, cfg.getString(ApplicationConfigConstants.AUTH0_AUDIENCE));
        String data = new String(Base64.getDecoder().decode(message.data));
        UPSKit[] kitsToLookFor = new Gson().fromJson(data, UPSKit[].class);
        PoolingDataSource<PoolableConnection> dataSource = CFUtil.createDataSource(MAX_CONNECTION, cfg.getString(ApplicationConfigConstants.CF_DSM_DB_URL));
        Arrays.stream(kitsToLookFor).forEach(
                kit -> {
                    logger.info("Checking possible actions for kit " + kit.getDsmKitRequestId());
                    if (StringUtils.isNotBlank(kit.getUpsPackage().getUpsShipmentId())) {
                        getUPSUpdate(dataSource, kit, cfg);
                    }
                    else {
                        insertShipmentAndPackageForNewKit(dataSource, kit, cfg);// for a new kit we first need to insert the UPSShipment
                    }
                }

        );


    }

    private void insertShipmentAndPackageForNewKit(PoolingDataSource<PoolableConnection> dataSource, UPSKit kit, Config cfg) {
        String insertedShipmentId = null;
        String[] insertedPackageIds = new String[2];
        String shippingKitPackageId = null;
        String returnKitPackageId = null;
        boolean gbfShippedTriggerDSSDelivered = false;
        logger.info("Inserting new kit information for kit " + kit.getDsmKitRequestId());
        final String SQL_INSERT_SHIPMENT = "INSERT INTO " + STUDY_MANAGER_SCHEMA + "ups_shipment" +
                "  ( dsm_kit_request_id )" +
                "  VALUES " +
                "  (?)";
        final String SQL_INSERT_UPSPackage = "INSERT INTO " + STUDY_MANAGER_SCHEMA + "ups_package" +
                "( ups_shipment_id ," +
                " tracking_number )" +
                "  VALUES " +
                "  ( ? ,?)," +
                "  ( ? ,?) ";

        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_SHIPMENT, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, kit.getDsmKitRequestId());
                int result = stmt.executeUpdate();
                if (result == 1) {
                    try (ResultSet rs = stmt.getGeneratedKeys()) {

                        if (rs.next()) {
                            insertedShipmentId = rs.getString(1);
                            logger.info("Added new ups shipment with id " + insertedShipmentId + " for " + kit.getDsmKitRequestId());
                        }
                    }
                    catch (Exception e) {
                        logger.error("Error getting id of new shipment ", e);
                        logger.error(e.getMessage());
                        return;
                    }
                }
                else {
                    logger.error("Error adding new ups shipment for kit w/ id " + kit.getDsmKitRequestId() + " it was updating " + result + " rows");
                    return;
                }

            }
            catch (Exception ex) {
                logger.error("Error preparing statement", ex);
                logger.error(ex.getMessage());
                return;
            }

            if (insertedShipmentId != null) {
                logger.info("Inserting new package information for kit " + kit.getDsmKitRequestId());
                try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_UPSPackage, Statement.RETURN_GENERATED_KEYS)) {
                    stmt.setString(1, insertedShipmentId);
                    stmt.setString(2, kit.getTrackingToId());//first row is the shipping one
                    stmt.setString(3, insertedShipmentId);
                    stmt.setString(4, kit.getTrackingReturnId());//second row is the return one
                    int result = stmt.executeUpdate();
                    if (result == 2) {
                        try (ResultSet rs = stmt.getGeneratedKeys()) {
                            int i = 0;
                            while (rs.next()) {
                                insertedPackageIds[i] = rs.getString(1);
                                i++;
                            }
                            if (i != 2) {
                                throw new RuntimeException("Didn't insert right amount of packages. Num of Packages = " + i);
                            }
                            shippingKitPackageId = insertedPackageIds[0];
                            returnKitPackageId = insertedPackageIds[1];
                            logger.info("Added new outbound ups package with id " + shippingKitPackageId + " for " + kit.getDsmKitRequestId()
                                    + " and added new inbound ups package with id " + returnKitPackageId + " for " + kit.getDsmKitRequestId());
                        }
                        catch (Exception e) {
                            logger.error("Error getting id of new inserted packages  for kit " + kit.getDsmKitRequestId(), e);
                            return;
                        }
                    }
                    else {
                        logger.error("Error adding new ups packages for kit w/ id " + kit.getDsmKitRequestId() + " it was updating " + result + " rows");
                        return;
                    }


                }
                catch (Exception ex) {
                    logger.error("Error preparing statement", ex);
                    logger.error(ex.getMessage());
                    return;
                }

            }
            gbfShippedTriggerDSSDelivered = InstanceSettings.getInstanceSettings(Integer.parseInt(kit.getDdpInstanceId()), conn).isGbfShippedTriggerDSSDelivered();
            conn.commit();
        }
        catch (SQLException ex) {
            logger.error("Trouble creating a connection to the database " + ex);
            logger.error(ex.getMessage());
        }
        finally {
            if (conn != null) {
                try {
                    conn.close();
                }
                catch (Throwable ex) {
                    logger.error("Could not close JDBC Connection ", ex);
                }
            }
        }
        UPSPackage upsPackageShipping = new UPSPackage(kit.getTrackingToId(), null, insertedShipmentId, shippingKitPackageId, null, null);
        UPSPackage upsPackageReturn = new UPSPackage(kit.getTrackingReturnId(), null, insertedShipmentId, returnKitPackageId, null, null);

        UPSKit kitShipping = new UPSKit(upsPackageShipping, kit.getKitLabel(), kit.getCE_order(), kit.getDsmKitRequestId(), kit.getExternalOrderNumber(), kit.getTrackingToId(), kit.getTrackingReturnId(), kit.getDdpInstanceId(), kit.getHruid(), gbfShippedTriggerDSSDelivered);
        UPSKit kitReturn = new UPSKit(upsPackageReturn, kit.getKitLabel(), kit.getCE_order(), kit.getDsmKitRequestId(), kit.getExternalOrderNumber(), kit.getTrackingToId(), kit.getTrackingReturnId(), kit.getDdpInstanceId(), kit.getHruid(), gbfShippedTriggerDSSDelivered);
        getUPSUpdate(dataSource, kitShipping, cfg);
        getUPSUpdate(dataSource, kitReturn, cfg);
    }


    public void getUPSUpdate(PoolingDataSource<PoolableConnection> dataSource, UPSKit kit, Config cfg) {
        logger.info("Checking UPS status for kit with external order number " + kit.getExternalOrderNumber());
        updateKitStatus(dataSource, kit, kit.isReturn(), kit.getDdpInstanceId(), cfg);
    }

    public UPSTrackingResponse lookupTrackingInfo(String trackingId) throws Exception {
        if (upsTracker != null) {
            return upsTracker.lookupTrackingInfo(trackingId);
        }
        else {
            throw new RuntimeException("UPSTracker should not be null!");
        }
    }

    public void updateKitStatus(PoolingDataSource<PoolableConnection> dataSource, UPSKit kit, boolean isReturn, String ddpInstanceId, Config cfg) {
        if (kit.getUpsPackage() != null) {
            String trackingId = kit.getUpsPackage().getTrackingNumber();
            UPSActivity lastActivity = kit.getUpsPackage().getActivity() == null ? null : kit.getUpsPackage().getActivity()[0];
            if (lastActivity != null && lastActivity.getStatus().isDelivery()) {
                this.logger.info("Tracking id " + trackingId + " is already delivered, not going to check UPS anymore");
                updateDeliveryInformation(dataSource, kit.getUpsPackage(), kit, cfg);
                return;
            }
            logger.info("Checking UPS status for " + trackingId + " for kit w/ external order number " + kit.getExternalOrderNumber());
            try {
                UPSTrackingResponse response = lookupTrackingInfo(trackingId);
                logger.info("UPS response for " + trackingId + " is " + response);//todo remove after tests
                if (response != null && response.getErrors() == null) {
                    updateStatus(dataSource, trackingId, lastActivity, response, isReturn, kit, ddpInstanceId, cfg);
                }
                else {
                    logError(trackingId, response.getErrors());
                }
            }
            catch (Exception e) {
                logger.error("Problem getting UPS update for kit " + kit.getUpsPackage().getTrackingNumber());
                e.printStackTrace();
            }
        }
        else {
            logger.error("Kit's UPSPackage was null for dsmKitRequestId: " + kit.getDsmKitRequestId());
        }
    }

    private void logError(String trackingId, UPSError[] errors) {
        String errorString = "";
        for (UPSError error : errors) {
            errorString += "Got Error: " + error.getCode() + " " + error.getMessage() + " For Tracking Number " + trackingId;
        }
        logger.error(errorString);
    }


    private void updateStatus(PoolingDataSource<PoolableConnection> dataSource, String trackingId, UPSActivity lastActivity, UPSTrackingResponse
            response, boolean isReturn, UPSKit kit, String ddpInstanceId, Config cfg) {
        if (response.getTrackResponse() != null) {
            UPSShipment[] shipment = response.getTrackResponse().getShipment();
            if (shipment != null && shipment.length > 0) {
                UPSPackage[] responseUpsPackages = shipment[0].getUpsPackageArray();
                if (responseUpsPackages != null && responseUpsPackages.length > 0) {
                    UPSPackage responseUpsPackage = responseUpsPackages[0];
                    UPSActivity[] activities = responseUpsPackage.getActivity();
                    if (activities != null) {
                        UPSActivity earliestPackageMovementEvent = responseUpsPackage.getEarliestPackageMovementEvent();
                        Instant earliestPackageMovement = null;
                        if (earliestPackageMovementEvent != null) {
                            earliestPackageMovement = earliestPackageMovementEvent.getInstant();
                        }
                        UPSActivity recentActivity = activities[0];
                        UPSStatus status = activities[0].getStatus();
                        String statusType = null;
                        if (status != null) {
                            statusType = status.getType();
                        }
                        if (lastActivity == null || (!lastActivity.equals(recentActivity))) {
                            updateTrackingInfo(dataSource, activities, statusType, lastActivity, trackingId,
                                    isReturn, kit, earliestPackageMovement, ddpInstanceId, cfg);
                        }
                        if (responseUpsPackage.getDeliveryDate() != null) {
                            updateDeliveryInformation(dataSource, responseUpsPackage, kit, cfg);
                        }
                    }
                }

            }
        }
    }

    private void updateDeliveryInformation(PoolingDataSource<PoolableConnection> dataSource, UPSPackage responseUpsPackage, UPSKit upsKit, Config cfg) {
        String SQL_UPDATE_PACKAGE_DELIVERY = "UPDATE " + STUDY_MANAGER_SCHEMA + "ups_package   " +
                "SET   " +
                "delivery_date = ?,   " +
                "delivery_time_start = ?,   " +
                "delivery_time_end = ?,   " +
                "delivery_time_type = ?   " +
                "WHERE ups_package_id = ? ";
        UPSDeliveryDate[] deliveryDates = responseUpsPackage.getDeliveryDate();
        UPSDeliveryDate currentDelivery = null;
        String deliveryDate = null;
        if (deliveryDates != null && deliveryDates.length > 0) {
            currentDelivery = deliveryDates[0];
            deliveryDate = currentDelivery.getDate();
        }
        String deliveryStartTime = null;
        String deliveryEndTime = null;
        String deliveryType = null;
        if (responseUpsPackage.getDeliveryTime() != null) {
            deliveryStartTime = responseUpsPackage.getDeliveryTime().getStartTime();
            deliveryEndTime = responseUpsPackage.getDeliveryTime().getEndTime();
            deliveryType = responseUpsPackage.getDeliveryTime().getType();
        }
        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_PACKAGE_DELIVERY)) {
                stmt.setString(1, deliveryDate);
                stmt.setString(2, deliveryStartTime);
                stmt.setString(3, deliveryEndTime);
                stmt.setString(4, deliveryType);
                stmt.setString(5, upsKit.getUpsPackage().getUpsPackageId());
                int r = stmt.executeUpdate();

                logger.info("Updated " + r + " rows adding delivery for tracking number " + responseUpsPackage.getTrackingNumber() + " for ups_package_id " + responseUpsPackage.getUpsPackageId());
                if (r != 1) {
                    logger.error(r + " rows updated in UPSPackege while updating delivery for " + upsKit.getUpsPackage().getUpsPackageId());
                }
                conn.commit();
            }
            catch (Exception ex) {
                logger.error("Error preparing statement", ex);
                logger.error(ex.getMessage());
            }
            conn.commit();
        }
        catch (SQLException e) {
            logger.error("Trouble creating a connection to the DB");
            logger.error(e.getMessage());
        }
        finally {
            if (conn != null) {
                try {
                    conn.close();
                }
                catch (Throwable ex) {
                    logger.error("Could not close JDBC Connection ", ex);
                    logger.error(ex.getMessage());
                }
            }
        }

    }


    private void updateTrackingInfo(PoolingDataSource<PoolableConnection> dataSource,
                                    UPSActivity[] activities,
                                    String statusType,
                                    UPSActivity lastActivity,
                                    String trackingId,
                                    boolean isReturn,
                                    UPSKit kit,
                                    Instant earliestInTransitTime,
                                    String ddpInstanceId,
                                    Config cfg) {
        final String INSERT_NEW_ACTIVITIES = "INSERT INTO " + STUDY_MANAGER_SCHEMA + "ups_activity    " +
                "(    " +
                "  ups_package_id  ,  " +
                "  ups_location  ,  " +
                "  ups_status_type  ,  " +
                "  ups_status_description  ,  " +
                "  ups_status_code  ,  " +
                "  ups_activity_date_time) " +
                "VALUES " +
                "( ?, ?, ?, ?, ?, ?)";
        final String SQL_SELECT_KIT_FOR_NOTIFICATION_EXTERNAL_SHIPPER = "select  eve.*,   request.ddp_participant_id,   request.ddp_label,   request.dsm_kit_request_id, request.ddp_kit_request_id, request.upload_reason, " +
                "        realm.ddp_instance_id, realm.instance_name, realm.base_url, realm.auth0_token, realm.notification_recipients, realm.migrated_ddp, kit.receive_date, kit.scan_date" +
                "        FROM " + STUDY_MANAGER_SCHEMA + "ddp_kit_request request, " + STUDY_MANAGER_SCHEMA + "ddp_kit kit, " + STUDY_MANAGER_SCHEMA + "event_type eve, " + STUDY_MANAGER_SCHEMA + "ddp_instance realm where request.dsm_kit_request_id = kit.dsm_kit_request_id and request.ddp_instance_id = realm.ddp_instance_id" +
                "        and not exists " +
                "                    (select 1 FROM " + STUDY_MANAGER_SCHEMA + "EVENT_QUEUE q" +
                "                    where q.DDP_INSTANCE_ID = realm.ddp_instance_id" +
                "                    and " +
                "                    q.EVENT_TYPE = eve.event_name" +
                "                    and " +
                "                    q.DSM_KIT_REQUEST_ID = request.dsm_kit_request_id " +
                "                    and q.event_triggered = true" +
                "                    )" +
                "        and (eve.ddp_instance_id = request.ddp_instance_id and eve.kit_type_id = request.kit_type_id) and eve.event_type = ? " +
                "         and realm.ddp_instance_id = ?" +
                "          and kit.dsm_kit_request_id = ?";
        logger.info("Inserting new activities for kit with package id " + kit.getUpsPackage().getUpsPackageId());
        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            for (int i = activities.length - 1; i >= 0; i--) {
                UPSActivity currentInsertingActivity = activities[i];
                if (lastActivity != null) {
                    if (lastActivity.getInstant() != null && (currentInsertingActivity.getInstant().equals(lastActivity.getInstant())
                            || currentInsertingActivity.getInstant().isBefore(lastActivity.getInstant()))) {
                        continue;
                    }
                }
                String activityDateTime = currentInsertingActivity.getSQLDateTimeString();



                try (PreparedStatement stmt = conn.prepareStatement(INSERT_NEW_ACTIVITIES)) {
                    stmt.setString(1, kit.getUpsPackage().getUpsPackageId());
                    stmt.setString(2, currentInsertingActivity.getLocation().getString());
                    stmt.setString(3, currentInsertingActivity.getStatus().getType());
                    stmt.setString(4, currentInsertingActivity.getStatus().getDescription());
                    stmt.setString(5, currentInsertingActivity.getStatus().getCode());
                    stmt.setString(6, activityDateTime);
                    int r = stmt.executeUpdate();

                    logger.info("Updated " + r + " rows for a new activity");
                    logger.info("Inserted new activity " + currentInsertingActivity.getStatus().getDescription() + " for package id " + kit.getUpsPackage().getUpsPackageId());
                    if (r != 1) {
                        logger.error(r + " is too big for 1 new activity");
                    }
                }
                catch (Exception ex) {
                    throw new RuntimeException("Error preparing statement for inserting a new activity  for package id " + kit.getUpsPackage().getUpsPackageId(), ex);
                }
            }
            String oldType = null;
            if (lastActivity != null && lastActivity.getStatus() != null) {
                oldType = lastActivity.getStatus().getType();
            }
            if (!isReturn) {
                if (shouldTriggerEventForKitOnItsWayToParticipant(statusType, oldType, kit.isGbfShippedTriggerDSSDelivered())) {
                    KitDDPNotification kitDDPNotification = KitDDPNotification.getKitDDPNotification(conn, SQL_SELECT_KIT_FOR_NOTIFICATION_EXTERNAL_SHIPPER + SELECT_BY_EXTERNAL_ORDER_NUMBER, new String[] { DELIVERED, ddpInstanceId, kit.getDsmKitRequestId(), kit.getExternalOrderNumber() }, 1);
                    if (kitDDPNotification != null) {
                        logger.info("Triggering DDP for kit going to participant with external order number: " + kit.getExternalOrderNumber());
                        EventUtil.triggerDDP(conn, kitDDPNotification, auth0Util);
                    }
                    else {
                        logger.error("delivered kitDDPNotification was null for " + kit.getExternalOrderNumber());
                    }
                }

            }
            else { // this is a return
                if (earliestInTransitTime != null && !kit.getCE_order()) {
                    // if we have the first date of an inbound event, create an order in CE
                    // using the earliest date of inbound event

                    boolean shouldPlaceCEOrder = true;
                    if (StringUtils.isNotBlank(kit.getKitLabel())) {
                        // if we are after the cutoff date and this is a longitudinal order, do not place the order
                        if (ignoreScheduledOrdersAfter != null) {
                            if (ignoreScheduledOrdersAfter.toInstant().isAfter(Instant.now())) {
                                shouldPlaceCEOrder = doesKitHaveUploadReason(conn, kit.getKitLabel());
                            }
                        }

                        if (shouldPlaceCEOrder) {
                            if (orderRegistrar == null || careEvolveAuth == null) {
                                Pair<Covid19OrderRegistrar, Authentication> careEvolveOrderingTools = createCEOrderRegistrar(cfg);
                                orderRegistrar = careEvolveOrderingTools.getLeft();
                                careEvolveAuth = careEvolveOrderingTools.getRight();

                            }
                            orderRegistrar.orderTest(careEvolveAuth, kit.getHruid(), kit.getMainKitLabel(), kit.getExternalOrderNumber(), earliestInTransitTime, conn, cfg);
                            logger.info("Placed CE order for kit with external order number " + kit.getExternalOrderNumber());
                            logger.info("Placed CE order for kit with label " + kit.getMainKitLabel() + " for time " + earliestInTransitTime);
                            kit.changeCEOrdered(conn, true);
                        }
                    }
                }
                else {
                    logger.info("No return events for " + kit.getMainKitLabel() + ".  Will not place order yet.");
                }
                if (shouldTriggerEventForReturnKitDelivery(statusType, oldType)) {
                    KitUtil.setKitReceived(conn, kit.getMainKitLabel());
                    logger.info("RECEIVED: " + trackingId);
                    KitDDPNotification kitDDPNotification = KitDDPNotification.getKitDDPNotification(conn, SQL_SELECT_KIT_FOR_NOTIFICATION_EXTERNAL_SHIPPER + SELECT_BY_EXTERNAL_ORDER_NUMBER, new String[] { RECEIVED, ddpInstanceId, kit.getDsmKitRequestId(), kit.getExternalOrderNumber() }, 1);
                    if (kitDDPNotification != null) {
                        logger.info("Triggering DDP for received kit with external order number: " + kit.getExternalOrderNumber());
                        EventUtil.triggerDDP(conn, kitDDPNotification, auth0Util);

                    }
                    else {
                        logger.error("received kitDDPNotification was null for " + kit.getExternalOrderNumber());
                    }
                }
            }
            conn.commit();
            logger.info("Updated status of tracking number " + trackingId + " to " + statusType + " from " + oldType + " for kit w/ external order number " + kit.getExternalOrderNumber());


        }
        catch (SQLException ex) {
            logger.error("Trouble connecting to DB " + ex);
            logger.error(ex.getMessage());
        }
        finally {
            if (conn != null) {
                try {
                    conn.close();
                }
                catch (Throwable ex) {
                    logger.error("Could not close JDBC Connection ", ex);
                }
            }
        }


    }

    /**
     * Queries the database to see whether there is an upload reason
     * for the given kit label
     */
    private boolean doesKitHaveUploadReason(Connection conn, String kitLabel) throws SQLException {
        boolean hasUploadReason = false;
        try (PreparedStatement stmt = conn.prepareStatement(SQL_GET_KIT_REQUEST + BY_KIT_LABEL)) {
            stmt.setString(1, kitLabel);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    KitRequestDto dto = new KitRequestDto(
                            rs.getInt(DBConstants.DSM_KIT_REQUEST_ID),
                            rs.getInt(DBConstants.DDP_INSTANCE_ID),
                            rs.getString(DBConstants.DDP_KIT_REQUEST_ID),
                            rs.getInt(DBConstants.KIT_TYPE_ID),
                            rs.getString(DBConstants.COLLABORATOR_PARTICIPANT_ID),
                            rs.getString(DBConstants.BSP_COLLABORATOR_PARTICIPANT_ID),
                            rs.getString(DBConstants.DDP_PARTICIPANT_ID),
                            rs.getString(DBConstants.DSM_LABEL),
                            rs.getString(DBConstants.CREATED_BY),
                            rs.getLong(DBConstants.CREATED_DATE),
                            rs.getString(DBConstants.EXTERNAL_ORDER_NUMBER),
                            rs.getLong(DBConstants.EXTERNAL_ORDER_DATE),
                            rs.getString(DBConstants.EXTERNAL_ORDER_STATUS),
                            rs.getString(DBConstants.EXTERNAL_RESPONSE),
                            rs.getString(DBConstants.UPLOAD_REASON),
                            rs.getTimestamp(DBConstants.ORDER_TRANSMITTED_AT));

                    if (!hasUploadReason && dto.hasUploadReason()) {
                        hasUploadReason = true;
                    }
                }
            }
        }
        return hasUploadReason;
    }

    /**
     * Determines whether or not a trigger should be sent to
     * study-server to respond to kit being sent to participant
     */
    private boolean shouldTriggerEventForKitOnItsWayToParticipant(String currentStatus, String previousStatus, boolean gbfShippedTriggerDSSDelivered) {
        if (gbfShippedTriggerDSSDelivered) {
            return false;
        }
        List<String> triggerStates = Arrays.asList(UPSStatus.DELIVERED_TYPE, UPSStatus.IN_TRANSIT_TYPE);
        return triggerStates.contains(currentStatus) && !triggerStates.contains(previousStatus);
    }

    /**
     * Determines whether or not a trigger should be sent to
     * study-server to respond to kit being delivered back at broad
     */
    private boolean shouldTriggerEventForReturnKitDelivery(String currentStatus, String previousStatus) {
        List<String> triggerStates = Arrays.asList(UPSStatus.DELIVERED_TYPE);
        return triggerStates.contains(currentStatus) && !triggerStates.contains(previousStatus);
    }


    private Pair<Covid19OrderRegistrar, Authentication> createCEOrderRegistrar(Config cfg) {
        Covid19OrderRegistrar orderRegistrar;
        String careEvolveSubscriberKey = cfg.getString(ApplicationConfigConstants.CARE_EVOLVE_SUBSCRIBER_KEY);
        String careEvolveServiceKey = cfg.getString(ApplicationConfigConstants.CARE_EVOLVE_SERVICE_KEY);
        Authentication careEvolveAuth = new Authentication(careEvolveSubscriberKey, careEvolveServiceKey);
        String careEvolveAccount = cfg.getString(ApplicationConfigConstants.CARE_EVOLVE_ACCOUNT);
        String careEvolveOrderEndpoint = cfg.getString(ApplicationConfigConstants.CARE_EVOLVE_ORDER_ENDPOINT);
        Integer careEvolveMaxRetries;
        Integer careEvolveRetyWaitSeconds;
        if (cfg.hasPath(ApplicationConfigConstants.CARE_EVOLVE_MAX_RETRIES)) {
            careEvolveMaxRetries = cfg.getInt(ApplicationConfigConstants.CARE_EVOLVE_MAX_RETRIES);
        }
        else {
            careEvolveMaxRetries = 5;
        }
        if (cfg.hasPath(ApplicationConfigConstants.CARE_EVOLVE_RETRY_WAIT_SECONDS)) {
            careEvolveRetyWaitSeconds = cfg.getInt(ApplicationConfigConstants.CARE_EVOLVE_RETRY_WAIT_SECONDS);
        }
        else {
            careEvolveRetyWaitSeconds = 10;
        }
        logger.info("Will retry CareEvolve at most {} times after {} seconds", careEvolveMaxRetries, careEvolveRetyWaitSeconds);
        Provider provider;
        provider = new Provider(cfg.getString(ApplicationConfigConstants.CARE_EVOLVE_PROVIDER_FIRSTNAME),
                cfg.getString(ApplicationConfigConstants.CARE_EVOLVE_PROVIDER_LAST_NAME),
                cfg.getString(ApplicationConfigConstants.CARE_EVOLVE_PROVIDER_NPI));
        orderRegistrar = new Covid19OrderRegistrar(careEvolveOrderEndpoint, careEvolveAccount, provider,
                careEvolveMaxRetries, careEvolveRetyWaitSeconds);
        Pair result = Pair.of(orderRegistrar, careEvolveAuth);
        return result;
    }


}




