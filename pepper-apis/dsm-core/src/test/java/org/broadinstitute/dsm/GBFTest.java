package org.broadinstitute.dsm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.model.KitRequest;
import org.broadinstitute.dsm.model.KitRequestSettings;
import org.broadinstitute.dsm.model.ddp.DDPParticipant;
import org.broadinstitute.dsm.model.gbf.*;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.DBUtil;
import org.broadinstitute.dsm.util.EasyPostUtil;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.SystemUtil;
import org.broadinstitute.dsm.util.externalShipper.ExternalShipper;
import org.broadinstitute.dsm.util.externalShipper.GBFRequestUtil;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

public class
GBFTest extends TestHelper {

    private String GBF_URL = "https://www.gbfmedical.com/oap/api/";
    private String ORDER_NUMBER = "WEB123ABC4D5";

    private static final Logger logger = LoggerFactory.getLogger(GBFTest.class);

    @BeforeClass
    public static void before() throws Exception {
        setupDB();
        ExternalShipper shipper = (ExternalShipper) Class.forName("org.broadinstitute.dsm.util.externalShipper.GBFRequestUtil").newInstance(); //to get blindTestExecutor instance
    }

    public String getApiKey() {
        String apiKey = null;
        JsonArray array = (JsonArray) (new JsonParser().parse(cfg.getString("externalShipper")));
        for (JsonElement ddpInfo : array) {
            if (ddpInfo.isJsonObject()) {
                if ("gbf".equals(ddpInfo.getAsJsonObject().get(ApplicationConfigConstants.SHIPPER_NAME).getAsString().toLowerCase())) {
                    if (ddpInfo != null) {
                        apiKey = ddpInfo.getAsJsonObject().get(ApplicationConfigConstants.API_KEY).getAsString();
                    }
                }
            }
        }
        return apiKey;
    }

    @Test
    public void orderGBFTestKit() throws Exception {
        String apiKey = getApiKey();
        if (apiKey != null) {
            Orders orders = new Orders();
            orders.setOrders(new ArrayList<>());
            Address address = new Address("1st Participant", "415 Main St", null, "Cambridge",
                    "MA", "02141", "US", "666666667");
            ShippingInfo shippingInfo = new ShippingInfo(null, "FEDEX_2_DAY", address);
            List<LineItem> lineItems = new ArrayList<>();
            lineItems.add(new LineItem("K-DFC-PROMISE", "1"));
            Order order = new Order(ORDER_NUMBER, "C7037154", "00001", shippingInfo, lineItems);
            orders.getOrders().add(order);

            String orderXml = GBFRequestUtil.orderXmlToString(Orders.class, orders);
            JSONObject payload = new JSONObject().put("orderXml", orderXml).put("test", true);
            String sendRequest = GBF_URL + GBFRequestUtil.ORDER_ENDPOINT;
            Response gbfResponse = GBFRequestUtil.executePost(Response.class, sendRequest, payload.toString(), apiKey);
            Assert.assertNotNull(gbfResponse);
            Assert.assertTrue(gbfResponse.isSuccess());
        }
        else {
            Assert.fail("No apiKey found");
        }
        apiKey = getApiKey();
        if (apiKey != null) {
            long testDate = 1595920000L; //change that to the date of testing!
            long start = testDate - SystemUtil.MILLIS_PER_DAY;
            long end = testDate;

            JSONObject payload = new JSONObject().put("startDate", SystemUtil.getDateFormatted(start)).put("endDate", SystemUtil.getDateFormatted(end));
            String sendRequest = GBF_URL + GBFRequestUtil.CONFIRM_ENDPOINT;
            Response gbfResponse = GBFRequestUtil.executePost(Response.class, sendRequest, payload.toString(), apiKey);
            Assert.assertNotNull(gbfResponse);
            Assert.assertTrue(StringUtils.isNotBlank(gbfResponse.getXML()));
        }
        else {
            Assert.fail("No apiKey found");
        }
        apiKey = getApiKey();
        if (apiKey != null) {
            List<String> orderNumbers = new ArrayList<>();
            orderNumbers.add(ORDER_NUMBER);
            JSONObject payload = new JSONObject().put("orderNumbers", orderNumbers);

            String sendRequest = GBF_URL + GBFRequestUtil.STATUS_ENDPOINT;
            Response gbfResponse = GBFRequestUtil.executePost(Response.class, sendRequest, payload.toString(), apiKey);

            Assert.assertNotNull(gbfResponse);
            Assert.assertTrue(gbfResponse.isSuccess());
            Assert.assertTrue(StringUtils.isBlank(gbfResponse.getErrorMessage()));
            List<Status> statuses = gbfResponse.getStatuses();
            Assert.assertTrue(statuses.size() == 1);
            Assert.assertTrue(!statuses.get(0).getOrderStatus().equals("NOT FOUND"));
        }
        else {
            Assert.fail("No apiKey found");
        }
    }
    @Test
    @Ignore
    public void reorderKits() throws Exception {
        String query = "Select *, from_unixtime(req.created_date/1000) as created_time " +
                "FROM prod_dsm_db.ddp_kit_request req \n" +
                "left join ddp_kit kit on \n" +
                "(req.dsm_kit_request_id = kit.dsm_kit_request_id)\n" +
                "LEFT JOIN (SELECT subK.kit_type_id, subK.external_name from ddp_kit_request_settings dkc   LEFT JOIN sub_kits_settings subK ON (subK.ddp_kit_request_settings_id = dkc.ddp_kit_request_settings_id)) as subkits ON (subkits.kit_type_id = req.kit_type_id)   " +
                "where \n" +
                "req.ddp_instance_id = ? \n" +
                "and (external_order_status is null or external_order_status = 'NOT FOUND')\n" +
                "and external_order_number is not null\n" +
                "and req.kit_type_id = 7\n" +
                "and from_unixtime(created_date/1000) like \"2021-02-27%\"\n" +
                "order by created_time DESC\n";
        GBFRequestUtil gbf =  new GBFRequestUtil();
        DDPInstance ddpInstance = DDPInstance.getDDPInstanceById(9);
        ArrayList<KitRequest> kitRequests =gbf.getKitRequestsNotDone(9, query);
        HashMap<Integer, KitRequestSettings> krs = KitRequestSettings.getKitRequestSettings("9");
        gbf.orderKitRequests(kitRequests, new EasyPostUtil(ddpInstance.getName()), krs.values().iterator().next(), null);



    }

    @Test
    public void statusGBFKit() throws Exception {
        String apiKey = "";
        if (apiKey != null) {
            List<String> orderNumbers = new ArrayList<>();
            orderNumbers.addAll(Arrays.asList(new String[] { "" }));
            //            logger.info("Starting the external shipper job");
            ExternalShipper shipper = (ExternalShipper) Class.forName("org.broadinstitute.dsm.util.externalShipper.GBFRequestUtil").newInstance(); //to get blindTestExecutor instance
            //            ArrayList<KitRequest> kitRequests = shipper.getKitRequestsNotDone(9);
            //            shipper.orderStatus(kitRequests);;
            JSONObject payload = new JSONObject().put("orderNumbers", orderNumbers);

            String sendRequest = GBF_URL + GBFRequestUtil.STATUS_ENDPOINT;
            Response gbfResponse = GBFRequestUtil.executePost(Response.class, sendRequest, payload.toString(), apiKey);

            Assert.assertNotNull(gbfResponse);
            Assert.assertTrue(gbfResponse.isSuccess());
            //            Assert.assertTrue(StringUtils.isBlank(gbfResponse.getErrorMessage()));
            //            List<Status> statuses = gbfResponse.getStatuses();
            //            Assert.assertTrue(statuses.size() == 1);
            //            Assert.assertTrue(!statuses.get(0).getOrderStatus().equals("NOT FOUND"));
        }
        else {
            Assert.fail("No apiKey found");
        }
    }

    @Test
    @Ignore
    public void confirmationGBFKit() throws Exception {
        String apiKey = "";
        if (apiKey != null) {
            long testDate = 1609172139000L; //change that to the date of testing!
            long start = 0;
            long end = testDate;

            JSONObject payload = new JSONObject().put("startDate", SystemUtil.getDateFormatted(start)).put("endDate", SystemUtil.getDateFormatted(end));
            String sendRequest = GBF_URL + GBFRequestUtil.CONFIRM_ENDPOINT;
            Response gbfResponse = GBFRequestUtil.executePost(Response.class, sendRequest, payload.toString(), apiKey);
            Assert.assertNotNull(gbfResponse);
            Assert.assertTrue(StringUtils.isNotBlank(gbfResponse.getXML()));

        }
        else {
            Assert.fail("No apiKey found");
        }
    }


    @Test
    @Ignore
    public void inputConfirmationGBFKit() throws Exception {
        String apiKey = "";
        if (apiKey != null) {
            long testDate = 1609172139000L; //change that to the date of testing!
            long start = 0L;
            long end = testDate;

            JSONObject payload = new JSONObject().put("startDate", SystemUtil.getDateFormatted(start)).put("endDate", SystemUtil.getDateFormatted(end));
            String sendRequest = GBF_URL + GBFRequestUtil.CONFIRM_ENDPOINT;
            Response gbfResponse = GBFRequestUtil.executePost(Response.class, sendRequest, payload.toString(), apiKey);

            GBFRequestUtil gbf = new GBFRequestUtil();
            String query = "SELECT * " +
                    "FROM ddp_kit_request req  " +
                    "LEFT JOIN ddp_kit kit ON (req.dsm_kit_request_id = kit.dsm_kit_request_id)  " +
                    "LEFT JOIN (SELECT subK.kit_type_id, subK.external_name from ddp_kit_request_settings dkc   LEFT JOIN sub_kits_settings subK ON (subK.ddp_kit_request_settings_id = dkc.ddp_kit_request_settings_id)) as subkits ON (subkits.kit_type_id = req.kit_type_id)   " +
                    "WHERE " +
                    "req.ddp_instance_id = ?  " +
                    "AND external_order_status = 'SHIPPED' " +
                    "AND external_response is null " +
                    "ORDER BY external_order_date ASC ";

            ArrayList<KitRequest> kitRequests = gbf.getKitRequestsNotDone(Integer.parseInt(DDPInstance.getDDPInstance("testboston").getDdpInstanceId()), query);
            HashMap<String, KitRequest> kits = new HashMap<>();
            for (KitRequest kit : kitRequests) {
                kits.put(kit.getExternalOrderNumber(), kit);
            }

            if (gbfResponse != null && StringUtils.isNotBlank(gbfResponse.getXML())) {
                logger.info("Confirmation xmls received! ");
                ShippingConfirmations shippingConfirmations = gbf.objectFromXMLString(ShippingConfirmations.class, gbfResponse.getXML());
                if (shippingConfirmations != null) {
                    List<ShippingConfirmation> confirmationList = shippingConfirmations.getShippingConfirmations();
                    logger.info("Number of confirmations received: " + confirmationList.size());
                    if (confirmationList != null && !confirmationList.isEmpty()) {
                        for (ShippingConfirmation confirmation : confirmationList) {
                            if (kits.containsKey(confirmation.getOrderNumber())) {
                                try {
                                    gbf.processingSingleConfirmation(gbfResponse, confirmation, 0);
                                }
                                catch (Exception e) {
                                    logger.error("Could not process confirmation for " + confirmation.getOrderNumber(), e);
                                }
                            }
                        }
                        DBUtil.updateBookmark(testDate, DBConstants.GBF_CONFIRMATION);
                    }
                    else {
                        logger.info("No shipping confirmation returned");
                    }
                }
            }
            Assert.assertNotNull(gbfResponse);
            Assert.assertTrue(StringUtils.isNotBlank(gbfResponse.getXML()));

        }
        else {
            Assert.fail("No apiKey found");
        }
    }

    @Test
    public void orderGBFKit() throws Exception {
        String externalOrderNumber = "T93CNUCZ8QHBSNRI1DJA";

        String orderLookup = "select \n" +
                "distinct req.ddp_participant_id\n" +
                "FROM prod_dsm_db.ddp_kit_request req\n" +
                "left join ddp_kit kit on\n" +
                "(req.dsm_kit_request_id = kit.dsm_kit_request_id)\n" +
                "where\n" +
                "req.ddp_instance_id = 9\n" +
                "and\n" +
                "req.external_order_number = ?";

        DDPInstance instance = DDPInstance.getDDPInstanceWithRole("testboston", DBConstants.HAS_KIT_REQUEST_ENDPOINTS);
          ArrayList<KitRequest> kitsToOrder = new ArrayList<>();

          EasyPostUtil easyPostUtil = new EasyPostUtil(null,"");
        Map<String, Map<String, Object>> elasticMap = ElasticSearchUtil.getDDPParticipantsFromES(instance.getName(), instance.getParticipantIndexES());

        HashMap<Integer, KitRequestSettings> kitRequestSettings = KitRequestSettings.getKitRequestSettings("9");

        GBFRequestUtil shipper = new GBFRequestUtil();

        TransactionWrapper.inTransaction(conn -> {
            try {
                try (PreparedStatement stmt = conn.prepareStatement(orderLookup)) {
                    stmt.setString(1, externalOrderNumber);

                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            String ddpParticipantId = rs.getString("ddp_participant_id");
                            DDPParticipant participant = ElasticSearchUtil.getParticipantAsDDPParticipant(elasticMap, ddpParticipantId);

                            kitsToOrder.add(new KitRequest(ddpParticipantId, participant.getShortId(), participant, externalOrderNumber));

                            for (KitRequestSettings kitRequestSetting : kitRequestSettings.values()) {
                                if ("gbf".equalsIgnoreCase(kitRequestSetting.getExternalShipper())) {
                                    kitRequestSetting.setCarrierTo("3rd Day Air Residential");
                                    kitRequestSetting.setServiceTo(kitRequestSetting.getCarrierTo());
                                    shipper.orderKitRequests(kitsToOrder, easyPostUtil, kitRequestSetting, null);
                                }
                            }

                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Could not order kit " + externalOrderNumber, e);
            }

            return null;
        });

    }
}
