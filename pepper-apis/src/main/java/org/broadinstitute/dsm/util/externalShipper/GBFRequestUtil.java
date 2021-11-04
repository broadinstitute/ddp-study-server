package org.broadinstitute.dsm.util.externalShipper;

import com.google.api.client.http.HttpStatusCodes;
import com.google.gson.Gson;
import lombok.NonNull;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.fluent.Executor;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.ddp.util.Utility;
import org.broadinstitute.dsm.DSMServer;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.InstanceSettings;
import org.broadinstitute.dsm.exception.ExternalShipperException;
import org.broadinstitute.dsm.model.*;
import org.broadinstitute.dsm.model.gbf.*;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.*;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

public class GBFRequestUtil implements ExternalShipper {

    private static final Logger logger = LoggerFactory.getLogger(GBFRequestUtil.class);

    public static final String SQL_SELECT_EXTERNAL_KIT_NOT_DONE = "SELECT * FROM (SELECT kt.kit_type_name, ddp_site.instance_name, ddp_site.ddp_instance_id, ddp_site.base_url, ddp_site.auth0_token, ddp_site.migrated_ddp, " +
            "                                    ddp_site.collaborator_id_prefix, req.bsp_collaborator_sample_id, req.ddp_participant_id, req.ddp_label, req.dsm_kit_request_id, req.bsp_collaborator_participant_id,  " +
            "                                    req.kit_type_id, req.external_order_status, req.external_order_number, req.external_order_date, req.external_response, kt.no_return, req.created_by, kit.kit_label FROM kit_type kt, ddp_kit_request req, ddp_kit kit, ddp_instance ddp_site " +
            "                                    WHERE req.ddp_instance_id = ddp_site.ddp_instance_id AND req.kit_type_id = kt.kit_type_id AND kit.dsm_kit_request_id = req.dsm_kit_request_id " +
            "                                    and kit.test_result is null and kit.CE_order is null " +
            "                                    and (kit.kit_label is null or kit.tracking_return_id is null or kit.tracking_to_id is null)" +
            ") " +
            "                                     AS request" +
            "                                    LEFT JOIN ddp_participant_exit ex ON (ex.ddp_instance_id = request.ddp_instance_id AND ex.ddp_participant_id = request.ddp_participant_id)" +
            "                                    LEFT JOIN (SELECT subK.kit_type_id, subK.external_name from ddp_kit_request_settings dkc " +
            "                                    LEFT JOIN sub_kits_settings subK ON (subK.ddp_kit_request_settings_id = dkc.ddp_kit_request_settings_id)) as subkits ON (subkits.kit_type_id = request.kit_type_id) " +
            "                                WHERE ex.ddp_participant_exit_id is null AND request.ddp_instance_id = ? AND (external_order_status not like '%CANCELLED%' or external_order_status is null) AND external_order_number IS NOT NULL " +
            "                                order by request.dsm_kit_request_id desc ";

    private String SQL_SELECT_KIT_FOR_NOTIFICATION_EXTERNAL_SHIPPER = "select  eve.*,   request.ddp_participant_id,   request.ddp_label,   request.dsm_kit_request_id, request.ddp_kit_request_id, request.upload_reason, " +
            "        realm.ddp_instance_id, realm.instance_name, realm.base_url, realm.auth0_token, realm.notification_recipients, realm.migrated_ddp, kit.receive_date, kit.scan_date" +
            "        FROM ddp_kit_request request, ddp_kit kit, event_type eve, ddp_instance realm where request.dsm_kit_request_id = kit.dsm_kit_request_id and request.ddp_instance_id = realm.ddp_instance_id" +
            "        and not exists " +
            "                    (select 1 FROM EVENT_QUEUE q" +
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

    private String SELECT_BY_EXTERNAL_ORDER_NUMBER = "and request.external_order_number = ?";
    String DELIVERED = "DELIVERED";

    private static final String SQL_SELECT_KIT_REQUEST_BY_EXTERNAL_ORDER_NUMBER = "SELECT dsm_kit_request_id FROM ddp_kit_request req WHERE external_order_number = ?";
    private static final String SQL_SELECT_SENT_KIT_FOR_NOTIFICATION_EXTERNAL_SHIPPER = "select  eve.*,   request.ddp_participant_id,   request.ddp_label, request.upload_reason, request.ddp_kit_request_id, request.dsm_kit_request_id, realm.ddp_instance_id, realm.instance_name, realm.base_url, realm.auth0_token, realm.notification_recipients, realm.migrated_ddp, kit.receive_date, kit.scan_date" +
            "        from ddp_kit_request request, ddp_kit kit, event_type eve, ddp_instance realm where request.dsm_kit_request_id = kit.dsm_kit_request_id and request.ddp_instance_id = realm.ddp_instance_id" +
            "        and (eve.ddp_instance_id = request.ddp_instance_id and eve.kit_type_id = request.kit_type_id) and eve.event_type = \"SENT\" and request.external_order_number is not null and request.external_order_number= ?";


    public static final String ORDER_ENDPOINT = "order";
    public static final String CONFIRM_ENDPOINT = "confirm";
    public static final String STATUS_ENDPOINT = "status";
    public final String CANCEL_ORDER_ENDPOINT = "cancelorder";

    //    private final String ORDERED = "ORDERED";
    private final String NOT_FOUND = "NOT FOUND"; //INDICATES: we have no record of the order number
    private final String RECEIVED = "RECEIVED"; //INDICATES: the order number is at GBF but has not begun processing
    private final String CANCELLED = "CANCELLED"; //INDICATES: the order was cancelled either via API or customer service
    private final String PROCESSING = "PROCESSING"; //INDICATES: the order is in our back-end system and being fulfilled
    private final String FORMS_PRINTED = "FORMS PRINTED"; //N/A to Promise project
    private final String DISTRIBUTED = "DISTRIBUTION"; //INDICATES: the order is in the final stages of shipping / Can no longer be ‘Cancelled’
    private final String SHIPPED = "SHIPPED"; //INDICATES: the order has been shipped and is with the carrier

    private static final String XML_NODE_EXPRESSION = "/ShippingConfirmations/ShippingConfirmation[@OrderNumber=\'%1\']";

    public static final String EXTERNAL_SHIPPER_NAME = "gbf";

    private static int additionalAttempts = 1;
    private static int sleepInMs = 500;

    private static Executor blindTrustEverythingExecutor;

    public GBFRequestUtil() {
        try {
            if (blindTrustEverythingExecutor == null) {
                blindTrustEverythingExecutor = Executor.newInstance(SecurityUtil.buildHttpClient());
            }
        }
        catch (Exception e) {
            logger.error("Starting up the blindTrustEverythingExecutor ", e);
            System.exit(-3);
        }
    }

    public String getExternalShipperName() {
        return EXTERNAL_SHIPPER_NAME;
    }

    public void orderKitRequests(ArrayList<KitRequest> kitRequests, EasyPostUtil easyPostUtil, KitRequestSettings kitRequestSettings, String shippingCarrier) throws Exception {
        if (kitRequests != null && !kitRequests.isEmpty()) {
            logger.info("Attempting to place orders with GBF");
            Orders orders = new Orders();
            orders.setOrders(new ArrayList<>());
            for (KitRequest kit : kitRequests) {
                Address address = null;
                logger.info("placing order for external order number: " + kit.getExternalOrderNumber());
                // kit was uploaded
                if (kit instanceof KitUploadObject) {
                    com.easypost.model.Address participantAddress = easyPostUtil.getAddress(((KitUploadObject) kit).getEasyPostAddressId());
                    address = new Address(participantAddress.getName(),
                            participantAddress.getStreet1(), participantAddress.getStreet2(), participantAddress.getCity(),
                            participantAddress.getState(), participantAddress.getZip(), participantAddress.getCountry(),
                            StringUtils.isNotBlank(((KitUploadObject) kit).getPhoneNumber()) ?
                            ((KitUploadObject) kit).getPhoneNumber() : participantAddress.getPhone());
                }
                // kit per ddp request
                else if (kit.getParticipant() != null) {
                    address = new Address(kit.getParticipant().getFirstName() + " " + kit.getParticipant().getLastName(),
                            kit.getParticipant().getStreet1(), kit.getParticipant().getStreet2(), kit.getParticipant().getCity(),
                            kit.getParticipant().getState(), kit.getParticipant().getPostalCode(), kit.getParticipant().getCountry(),
                            kitRequestSettings.getPhone());

                }
                if (!address.isComplete()) {
                    logger.error("Address is not complete for kit with external order number " + kit.getExternalOrderNumber());
                    continue;
                }
                else if (address != null) {
                    if (!(kit instanceof KitUploadObject)) {
                        shippingCarrier = kitRequestSettings.getServiceTo();
                    }
                    ShippingInfo shippingInfo = new ShippingInfo(kitRequestSettings.getCarrierToAccountNumber(), shippingCarrier, address);
                    List<LineItem> lineItems = new ArrayList<>();
                    lineItems.add(new LineItem(kitRequestSettings.getExternalShipperKitName(), "1"));
                    Order order = new Order(kit.getExternalOrderNumber(), kitRequestSettings.getExternalClientId(), kit.getParticipantId(), shippingInfo, lineItems);
                    orders.getOrders().add(order);
                }
                else {
                    logger.error("No address for participant w/ " + kit.getParticipantId());
                }
            }
            if (!orders.getOrders().isEmpty()) {
                String orderXml = GBFRequestUtil.orderXmlToString(Orders.class, orders);

                //                logger.info("orderXML: " + orderXml);
                boolean test = DSMServer.isTest(getExternalShipperName());//true for dev in `not-secret.conf`
                JSONObject payload = new JSONObject().put("orderXml", orderXml).put("test", test);
                String sendRequest = DSMServer.getBaseUrl(getExternalShipperName()) + ORDER_ENDPOINT;
                String apiKey = DSMServer.getApiKey(getExternalShipperName());
                Response gbfResponse = null;
                int totalAttempts = 2 + additionalAttempts;
                Exception ex = null;
                try {
                    for (int i = 1; i <= totalAttempts; i++) {
                        //if this isn't the first attempt let's wait a little before retrying...
                        if (i > 1) {
                            logger.info("Sleeping before request retry for " + sleepInMs + " ms.");
                            Thread.sleep(sleepInMs);
                            logger.info("Sleeping done.");
                        }
                        try {
                            gbfResponse = executePost(Response.class, sendRequest, payload.toString(), apiKey);
                            break;
                        }
                        catch (Exception newEx) {
                            logger.warn("Send request failed (attempt #" + i + " of " + totalAttempts + "): ", newEx);
                            ex = newEx;
                        }
                    }
                }
                catch (Exception outerEx) {
                    throw new RuntimeException("Unable to send requests.", ex);
                }
                if (gbfResponse != null && gbfResponse.isSuccess()) {
                    logger.info("Ordered kits");
                }
                else {
                    throw new ExternalShipperException("Unable to order kits after retry.", ex);
                }
            }
        }
    }

    //  Status is as it sounds, a real-time status of the order progression through the GBF internal process
    // 'RECEIVED', 'PROCESSING', 'SHIPPED', 'DISTRIBUTION', 'FORMS PRINTED', 'CANCELLED', 'NOT FOUND', 'SHIPPED (SIMULATED)'
    // return the actual tube barcode for all tubes but only after the kit is shipped (around 7:00pm)
    public void orderStatus(Connection conn, KitRequest kit, int instanceId, boolean gbfShippedTriggerDSSDelivered) throws Exception {
        logger.info("checking status of " + kit.getExternalOrderNumber() + " for participant " + kit.getParticipantId());
        List<String> orderNumbers = new ArrayList<>();
        orderNumbers.addAll(Arrays.asList(new String[] { kit.getExternalOrderNumber() }));
        JSONObject payload = new JSONObject().put("orderNumbers", orderNumbers);
        String sendRequest = DSMServer.getBaseUrl(getExternalShipperName()) + STATUS_ENDPOINT;
        Response gbfResponse = executePost(Response.class, sendRequest, payload.toString(), DSMServer.getApiKey(getExternalShipperName()));
        if (gbfResponse != null && gbfResponse.isSuccess()) {
            List<Status> statuses = gbfResponse.getStatuses();
            logger.info("Got a list of " + statuses.size() + " responses from GBF status");
            if (statuses != null && !statuses.isEmpty()) {
                for (Status status : statuses) {
                    logger.info("Got Kit status in GBF Response is " + status.getOrderStatus() + " for " + status.getOrderNumber());
                    if (!status.getOrderStatus().equals(kit.getExternalOrderStatus())) {
                        logger.info("Kit status from GBF is changed from " + kit.getExternalOrderStatus() + " to " + status.getOrderStatus() + " for kit: " + kit.getExternalOrderNumber());
                    }
                    KitDDPNotification kitDDPNotification = KitDDPNotification.getKitDDPNotification(SQL_SELECT_SENT_KIT_FOR_NOTIFICATION_EXTERNAL_SHIPPER, kit.getExternalOrderNumber(), 2);//todo change this to the number of subkits but for now 2 for test boston works
                    if (status.getOrderStatus().equals(NOT_FOUND) && StringUtils.isNotBlank(kit.getExternalOrderStatus()) && kit.getExternalOrderStatus().equals("NOT FOUND")
                            && System.currentTimeMillis() - kit.getExternalOrderDate() >= TimeUnit.HOURS.toMillis(24)) {
                        List<String> dsmKitRequestIds = getDSMKitRequestIds(status.getOrderNumber());
                        if (dsmKitRequestIds != null && !dsmKitRequestIds.isEmpty()) {
                            for (String dsmKitRequestId : dsmKitRequestIds) {
                                KitRequestExternal.updateKitRequest(conn, status.getOrderStatus(), System.currentTimeMillis(), dsmKitRequestId);// in order to update time for the  next 24 hour check we need this
                            }
                        }
                        logger.warn("Kit Request with external order number " + kit.getExternalOrderNumber() + "has not been shipped in the last 24 hours! ");//todo pegah uncomment for production
                    }
                    else if (status.getOrderStatus().contains(SHIPPED) && (StringUtils.isBlank(kit.getExternalOrderStatus()) ||
                            !kit.getExternalOrderStatus().contains(SHIPPED))) {
                        if (kitDDPNotification != null) {
                            logger.info("Triggering DDP for shipped kit with external order number: " + kit.getExternalOrderNumber());
                            if (gbfShippedTriggerDSSDelivered) {
                                KitDDPNotification kitDeliveredNotification = KitDDPNotification.getKitDDPNotification(conn, SQL_SELECT_KIT_FOR_NOTIFICATION_EXTERNAL_SHIPPER + SELECT_BY_EXTERNAL_ORDER_NUMBER, new String[] { DELIVERED, String.valueOf(instanceId), kit.getDsmKitRequestId(), kit.getExternalOrderNumber() }, 1);
                                if (kitDeliveredNotification != null) {
                                    logger.info("Triggering DDP for kit 'DELIVERED' with external order number: " + kit.getExternalOrderNumber());
                                    EventUtil.triggerDDP(conn, kitDeliveredNotification);
                                }
                                else {
                                    logger.error("delivered kitDDPNotification was null for " + kit.getExternalOrderNumber());
                                }
                            }
                            EventUtil.triggerDDP(conn, kitDDPNotification);
                        }
                        else {
                            logger.error("kitDDPNotification was null for " + kit.getExternalOrderNumber());
                        }
                    }
                    else if (status.getOrderStatus().contains("CANCELLED") && (StringUtils.isBlank(kit.getExternalOrderStatus()) ||
                            (StringUtils.isNotBlank(kit.getExternalOrderStatus()) && !kit.getExternalOrderStatus().contains("CANCELLED")))) {//todo uncomment for prod
                        logger.error("Kit Request with external order number " + kit.getExternalOrderNumber() + "has got cancelled by GBF!");//todo pegah uncomment for production
                    }
                    if (StringUtils.isBlank(kit.getExternalOrderStatus()) ||
                            !kit.getExternalOrderStatus().equals(status.getOrderStatus())) {// if changed
                        List<String> dsmKitRequestIds = getDSMKitRequestIds(status.getOrderNumber());
                        if (dsmKitRequestIds != null && !dsmKitRequestIds.isEmpty()) {
                            for (String dsmKitRequestId : dsmKitRequestIds) {
                                KitRequestExternal.updateKitRequest(conn, status.getOrderStatus(), System.currentTimeMillis(), dsmKitRequestId);
                            }
                        }
                    }
                }
            }
            else {
                new RuntimeException("Failed to check status of kits from " + EXTERNAL_SHIPPER_NAME + ": " + gbfResponse.getErrorMessage());
            }
        }
        else{
            logger.error("GBFStatus call was not successful for order number: "+kit.getExternalOrderNumber());
        }
    }

    public static void processingSingleConfirmation(Response gbfResponse, ShippingConfirmation confirmation) throws Exception {
        logger.info("Got confirmation for " + confirmation.getOrderNumber());
        Node node = GBFRequestUtil.getXMLNode(gbfResponse.getXML(), XML_NODE_EXPRESSION.replace("%1", confirmation.getOrderNumber()));
        String externalResponse = getStringFromNode(node);
        Item item = confirmation.getItem();
        if (item != null && StringUtils.isNotBlank(externalResponse)) {
            inTransaction((conn) -> {
                List<String> dsmKitRequestIds = getDSMKitRequestIds(conn, confirmation.getOrderNumber()); //dsm_kit_request_id
                if (dsmKitRequestIds != null && !dsmKitRequestIds.isEmpty()) {
                    int counter = 0;
                    for (String dsmKitRequestId : dsmKitRequestIds) {
                        //update external shipper response at request
                        KitRequestExternal.updateKitRequestResponse(conn, externalResponse, dsmKitRequestId);

                        //update kit shipping information
                        String kitLabel = item.getTubeSerial();
                        if (counter > 0) {
                            kitLabel += "_" + counter;
                        }
                        KitRequestExternal.updateKitRequestResponse(conn, confirmation.getTracking(), item.getReturnTracking(),
                                kitLabel, SystemUtil.getLongFromDateString(confirmation.getShipDate()), EXTERNAL_SHIPPER_NAME,
                                dsmKitRequestId);
                        counter++;
                        logger.info("Updated confirmation information for : " + dsmKitRequestId + " " + kitLabel);
                    }
                } else {
                    logger.error("No kit requests found for kit with order number: " + confirmation.getOrderNumber());
                }
                return null;
            });
        } else {
            logger.error("No items or external response for order " + confirmation.getOrderNumber());
        }
    }

    // The confirmation, dependent upon level of detail required, is a shipping receipt to prove completion.
    // Confirmation may include order number, client(participant) ID, outbound tracking number, return tracking number(s), line item(s), kit serial number(s), etc.
    public void orderConfirmation(long startDate, long endDate) throws Exception {
        JSONObject payload = new JSONObject().put("startDate", SystemUtil.getDateFormatted(startDate)).put("endDate", SystemUtil.getDateFormatted(endDate));
        String sendRequest = DSMServer.getBaseUrl(getExternalShipperName()) + CONFIRM_ENDPOINT;
        logger.info("payload: " + payload.toString());
        Response gbfResponse = executePost(Response.class, sendRequest, payload.toString(), DSMServer.getApiKey(getExternalShipperName()));

        if (gbfResponse != null && StringUtils.isNotBlank(gbfResponse.getXML())) {
            logger.info("Confirmation xmls received! ");
            ShippingConfirmations shippingConfirmations = objectFromXMLString(ShippingConfirmations.class, gbfResponse.getXML());
            if (shippingConfirmations != null) {
                List<ShippingConfirmation> confirmationList = shippingConfirmations.getShippingConfirmations();
                if (confirmationList != null && !confirmationList.isEmpty()) {
                    Collections.shuffle(confirmationList);
                    logger.info("Number of confirmations received: " + confirmationList.size());
                    for (ShippingConfirmation confirmation : confirmationList) {
                        try {
                            processingSingleConfirmation(gbfResponse, confirmation);
                        }
                        catch (Exception e) {
                            logger.error("Could not process confirmation for " + confirmation.getOrderNumber(), e);
                        }
                    }
                    DBUtil.updateBookmark(endDate, DBConstants.GBF_CONFIRMATION); //TODO can be removed because not used anymore
                    logger.info("Finished adding confirmations into db!");
                }
                else {
                    logger.info("No ShippingConfirmation Elements");
                }
            }
            else {
                logger.info("No shipping confirmation returned");
            }
        }
    }

    public void orderCancellation(ArrayList<KitRequest> kitRequests) throws Exception {
        throw new NotImplementedException("Not implemented yet");
    }

    public void updateOrderStatusForPendingKitRequests(int instanceId) {
        updateOrderStatusForPendingKitRequests(instanceId, SQL_SELECT_EXTERNAL_KIT_NOT_DONE);
    }

    /**
     * Streams through the list of pending requests from the
     * database and updates the status based on the latest
     * details from GBF
     */
    private void updateOrderStatusForPendingKitRequests(int instanceId, String query) {
        boolean gbfShippedTriggerDSSDelivered = InstanceSettings.getInstanceSettings(instanceId).isGbfShippedTriggerDSSDelivered();
        final AtomicInteger numOrdersProcessed = new AtomicInteger();
        final Set<String> queriedOrderIds = new HashSet<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, instanceId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String ddpParticipantId = rs.getString(DBConstants.DDP_PARTICIPANT_ID);
                        if (StringUtils.isNotBlank(ddpParticipantId)) {
                            KitRequest kitRequest = new KitRequest(rs.getString(DBConstants.DSM_KIT_REQUEST_ID), ddpParticipantId,
                                    null, null, rs.getString(DBConstants.EXTERNAL_ORDER_NUMBER), null,
                                    rs.getString(DBConstants.EXTERNAL_ORDER_STATUS),
                                    rs.getString("subkits." + DBConstants.EXTERNAL_KIT_NAME),
                                    rs.getLong(DBConstants.EXTERNAL_ORDER_DATE));
                            try {
                                // keep track of which request ids we've already asked about, since this
                                // result set may show subkits with the same external order id
                                if (!queriedOrderIds.contains(kitRequest.getExternalOrderNumber())) {
                                    orderStatus(conn, kitRequest, instanceId, gbfShippedTriggerDSSDelivered);
                                    numOrdersProcessed.incrementAndGet();
                                    queriedOrderIds.add(kitRequest.getExternalOrderNumber());
                                }
                            } catch (Exception e) {
                                logger.error("Could not check status of kit request " + kitRequest.getExternalOrderNumber(), e);
                            }
                            logger.info("Processed  " + numOrdersProcessed.get() + " incomplete orders");
                        }
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error looking up kit requests  ", results.resultException);
        }
    }

    /**
     * Should only be used for testing.  Scaling issues
     * will lead to crashes if used in production.
     */
    public ArrayList<KitRequest> getKitRequestsNotDone(int instanceId) {
        return getKitRequestsNotDone(instanceId,SQL_SELECT_EXTERNAL_KIT_NOT_DONE );
    }

    public  ArrayList<KitRequest>   getKitRequestsNotDone(int instanceId, String query) {
        DDPInstance ddpInstance = DDPInstance.getDDPInstanceById(instanceId);

        ArrayList<KitRequest> kitRequests = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, instanceId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String ddpParticipantId = rs.getString(DBConstants.DDP_PARTICIPANT_ID);
//                        logger.info("querying ES for ddpParticipantId: "+ddpParticipantId);
                        if (StringUtils.isNotBlank(ddpParticipantId)) {
//                            Map participantsESData = getParticipant(ddpInstance, ddpParticipantId);
//                            if (participantsESData != null && !participantsESData.isEmpty()) {
//                                DDPParticipant ddpParticipant = ElasticSearchUtil.getParticipantAsDDPParticipant(participantsESData, ddpParticipantId);
//                                logger.info("ddpParticipant found: "+ddpParticipant.getParticipantId());
//                                if (ddpParticipant != null) {
                            kitRequests.add(new KitRequest(rs.getString(DBConstants.DSM_KIT_REQUEST_ID), ddpParticipantId,
                                    null, null, rs.getString(DBConstants.EXTERNAL_ORDER_NUMBER), null,
                                    rs.getString(DBConstants.EXTERNAL_ORDER_STATUS),
                                    rs.getString("subkits." + DBConstants.EXTERNAL_KIT_NAME),
                                    rs.getLong(DBConstants.EXTERNAL_ORDER_DATE)));
//                                }
//                            }
//                            else {
//                                logger.error("Participant not found in ES! " + ddpParticipantId);
//                            }

                        }
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error looking up kit requests  ", results.resultException);
        }
        logger.info("Found " + kitRequests.size() + " kit requests which do not have an end status yet");
        return kitRequests;
    }

    public static  List<String> getDSMKitRequestIds(@NonNull Connection conn, String externalOrderNumber) {
        List<String> dsmKitRequestIds = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_KIT_REQUEST_BY_EXTERNAL_ORDER_NUMBER)) {
            stmt.setString(1, externalOrderNumber);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    dsmKitRequestIds.add(rs.getString(DBConstants.DSM_KIT_REQUEST_ID));
                }
            }
        }
        catch (SQLException ex) {
            throw new RuntimeException("Error looking up kit requests for " + externalOrderNumber, ex);
        }
        return dsmKitRequestIds;
    }

    public static  List<String> getDSMKitRequestIds(String externalOrderNumber) {
        List<String> dsmKitRequestIds = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_KIT_REQUEST_BY_EXTERNAL_ORDER_NUMBER)) {
                stmt.setString(1, externalOrderNumber);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        dsmKitRequestIds.add(rs.getString(DBConstants.DSM_KIT_REQUEST_ID));
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error looking up kit requests  ", results.resultException);
        }
        //        logger.info("Found " + dsmKitRequestIds.size() + " kit requests with given external_order_number");
        return dsmKitRequestIds;
    }

    private static List<KitRequest> getKitRequest(@NonNull List<KitRequest> kitRequests, @NonNull String externalOrderNumber, String subKitName, int tubeNumber) {
        int counter = -1;
        ArrayList<KitRequest> kitRequestsResult = new ArrayList<>();
        for (KitRequest kitRequest : kitRequests) {
            if (externalOrderNumber.equals(kitRequest.getExternalOrderNumber())) {
                if (StringUtils.isNotBlank(subKitName)) {
                    if (subKitName.equals(kitRequest.getExternalKitName())) {
                        kitRequestsResult.add(kitRequest);
                        counter++;
                        if (counter == tubeNumber) {
                            return kitRequestsResult;
                        }
                    }
                }
                else {
                    return kitRequestsResult;
                }
            }
        }
        return null;
    }

    public static <T> T executePost(Class<T> responseClass, String sendRequest, Object objectToPost, String apiKey) throws Exception {
        logger.info("Requesting data from GBF w/ " + sendRequest);
        org.apache.http.client.fluent.Request request = SecurityUtil.createPostRequestWithHeader(sendRequest, apiKey, objectToPost);

        //        T object = request.execute().handleResponse(res -> { //TODO remove blind trust!!!
        T object = blindTrustEverythingExecutor.execute(request).handleResponse(res -> {
            int responseCodeInt = res.getStatusLine().getStatusCode();
            try {
                if (responseCodeInt != HttpStatusCodes.STATUS_CODE_OK) {
                    logger.error("Got " + responseCodeInt + " from " + sendRequest);
                    if (responseCodeInt == HttpStatusCodes.STATUS_CODE_SERVER_ERROR) {
                        throw new RuntimeException("Got " + responseCodeInt + " from " + sendRequest);
                    }
                    else {
                        logger.error(Utility.getHttpResponseAsString(res));
                    }
                    return null;
                }
                else {
                    String response = Utility.getHttpResponseAsString(res);
                    return new Gson().fromJson(response, responseClass);
                }
            }
            catch (Exception e) {
                throw new RuntimeException("Failed to read HttpResponse ", e);
            }
        });
        return object;
    }

    public static <T> String orderXmlToString(Class<T> clazz, T object) throws JAXBException {
        StringWriter sw = new StringWriter();
        JAXBContext jaxbContext = JAXBContext.newInstance(clazz);
        Marshaller jaxbMarshaller = jaxbContext.createMarshaller();

        // output pretty printed
        jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, false);
        jaxbMarshaller.marshal(object, sw);
        return sw.toString();
    }

    public static <T> T objectFromXMLString(Class<T> clazz, String xml) throws Exception {
        JAXBContext jaxbContext = JAXBContext.newInstance(clazz);
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();

        StringReader reader = new StringReader(xml);

        XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
        XMLStreamReader xmlStreamReader = xmlInputFactory.createXMLStreamReader(reader);

        T object = (T) unmarshaller.unmarshal(xmlStreamReader);

        return object;
    }

    public static Node getXMLNode(String xml, String expression) throws Exception {
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        ByteArrayInputStream stream = new ByteArrayInputStream(xml.getBytes());
        Document document = builder.parse(stream);

        XPath xPath = XPathFactory.newInstance().newXPath();
        return (Node) xPath.compile(expression).evaluate(document, XPathConstants.NODE);
    }

    public static String getStringFromNode(Node node) throws Exception {
        StreamResult xmlOutput = new StreamResult(new StringWriter());
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.transform(new DOMSource(node), xmlOutput);
        return xmlOutput.getWriter().toString();
    }

    public static Map getParticipants(String realm) {
        DDPInstance ddpInstance = DDPInstance.getDDPInstanceWithRole(realm, DBConstants.NEEDS_NAME_LABELS);
        Map<String, Map<String, Object>> participantsESData = null;
        if (StringUtils.isNotBlank(ddpInstance.getParticipantIndexES())) {
            participantsESData = ElasticSearchUtil.getDDPParticipantsFromES(ddpInstance.getName(), ddpInstance.getParticipantIndexES());
        }
        return participantsESData;
    }

    public static Map getParticipant(DDPInstance ddpInstance, String ddpParticipantId) {
        Map<String, Map<String, Object>> participantsESData = null;
        if (StringUtils.isNotBlank(ddpInstance.getParticipantIndexES())) {
            participantsESData = ElasticSearchUtil.getFilteredDDPParticipantsFromES(ddpInstance, ElasticSearchUtil.BY_GUID + ddpParticipantId);
        }
        return participantsESData;
    }

    public static void markKitRequestAsOrdered(ArrayList<KitRequest> kitRequests) {
        String query = "UPDATE ddp_kit_requests set external_order_status = 'ORDERED', external_order_date = ? where dsm_kit_request_id = ? ";
        for (KitRequest kitRequest : kitRequests) {
            SimpleResult results = inTransaction((conn) -> {
                SimpleResult dbVals = new SimpleResult();
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setLong(1, System.currentTimeMillis() / 1000);
                    stmt.setString(2, kitRequest.getDsmKitRequestId());
                    int result = stmt.executeUpdate();
                    if (result != 1) {
                        throw new RuntimeException("Error updating status as ordered");
                    }
                }
                catch (SQLException ex) {
                    dbVals.resultException = ex;
                }
                return dbVals;
            });
        }

    }

}
