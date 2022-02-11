package org.broadinstitute.dsm;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.ddp.util.BasicTriggerListener;
import org.broadinstitute.dsm.jobs.ExternalShipperJob;
import org.broadinstitute.dsm.model.KitRequest;
import org.broadinstitute.dsm.model.KitRequestSettings;
import org.broadinstitute.dsm.util.DBTestUtil;
import org.broadinstitute.dsm.util.EasyPostUtil;
import org.broadinstitute.dsm.util.SystemUtil;
import org.broadinstitute.dsm.util.TestUtil;
import org.broadinstitute.dsm.util.externalShipper.ExternalShipper;
import org.broadinstitute.dsm.util.externalShipper.GBFRequestUtil;
import org.broadinstitute.dsm.util.triggerListener.ExternalShipperTriggerListener;
import org.junit.*;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.impl.matchers.KeyMatcher;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Map;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;
//import static org.mockserver.model.HttpRequest.request;
//import static org.mockserver.model.HttpResponse.response;
import static org.quartz.CronScheduleBuilder.cronSchedule;

public class QuartzExternalShipperTest extends TestHelper {

    public static final String CHECK_EXTERNAL_SHIPPER_REQUEST = "select * from ddp_kit_request where ddp_participant_id = ?";
    public static final String CHECK_EXTERNAL_SHIPPER_KIT = "select * from ddp_kit_request req, ddp_kit kit where req.dsm_kit_request_id = kit.dsm_kit_request_id and ddp_participant_id = ?";
    public static final String CHECK_EXTERNAL_SHIPPER_REQUEST_COUNT = "select count(*) from ddp_kit_request where ddp_participant_id = ?";

    @BeforeClass
    public static void first() throws Exception {
        setupDB();
        startDSMServer();
        startMockServer();
        setupUtils();

        DBTestUtil.deleteAllKitData("00004");
        DBTestUtil.deleteAllKitData("00003");
    }

    @AfterClass
    public static void last() {
        stopMockServer();
        stopDSMServer();
        cleanDB();
        cleanupDB();

        DBTestUtil.deleteAllKitData("00004");
        DBTestUtil.deleteAllKitData("00003");
    }

//    @Ignore("external shipper is not used currently and code is commented out")
    @Test
    public void externalShipperJob() throws Exception {
//        uploadKit();

        GBFRequestUtil shipper = new GBFRequestUtil();
        ArrayList<KitRequest> kitRequests = shipper.getKitRequestsNotDone(15);
        Map<Integer, KitRequestSettings> kitRequestSettingsMap = KitRequestSettings.getKitRequestSettings("15");
        shipper.orderKitRequests(kitRequests, new EasyPostUtil("testboston"), kitRequestSettingsMap.get(11), null       );


        String externalOrderNumber1 = DBTestUtil.getQueryDetail(CHECK_EXTERNAL_SHIPPER_REQUEST, "00004", "external_order_number");
        String externalOrderNumber2 = DBTestUtil.getQueryDetail(CHECK_EXTERNAL_SHIPPER_REQUEST, "00003", "external_order_number");
//        String gbfResponse = TestUtil.readFile("gbf/ConfirmationResponse.json").replace("%1", externalOrderNumber1).replace("%2", externalOrderNumber2);
//        mockDDP.when(
//                request().withPath("/confirm"))
//                .respond(response().withStatusCode(200).withBody(gbfResponse));
//        gbfResponse = TestUtil.readFile("gbf/StatusResponse.json").replace("%1", externalOrderNumber1).replace("%2", externalOrderNumber2);
//        mockDDP.when(
//                request().withPath("/status"))
//                .respond(response().withStatusCode(200).withBody(gbfResponse));

        Scheduler scheduler = new StdSchedulerFactory().getScheduler();
        createJob(scheduler);

        // wait for trigger to finish repeats
        try {
            Thread.sleep(300L * 1000L); // 5 min
            scheduler.shutdown(true);
        }
        catch (Exception e) {
            throw new RuntimeException("something went wrong, while waiting for quartz jon to finish...", e);
        }


        // check that status was changed - all external columns should be filled at the end of the job...
        checkDBValues(CHECK_EXTERNAL_SHIPPER_REQUEST, "00004", "external_order_status", true);
        checkDBValues(CHECK_EXTERNAL_SHIPPER_REQUEST, "00003", "external_order_status", true);
        checkDBValues(CHECK_EXTERNAL_SHIPPER_REQUEST, "00004", "external_order_number", true);
        checkDBValues(CHECK_EXTERNAL_SHIPPER_REQUEST, "00003", "external_order_number", true);
//        Assert.assertEquals("ORD3343", DBTestUtil.getQueryDetail(CHECK_EXTERNAL_SHIPPER_REQUEST, "00003", "external_order_number")); //2018-06-04
//        Assert.assertEquals("ORD3344", DBTestUtil.getQueryDetail(CHECK_EXTERNAL_SHIPPER_REQUEST, "00004", "external_order_number")); //2018-06-04
        checkDBValues(CHECK_EXTERNAL_SHIPPER_REQUEST, "00004", "external_order_date", true);
        checkDBValues(CHECK_EXTERNAL_SHIPPER_REQUEST, "00003", "external_order_date", true);
//        checkDBValues(CHECK_EXTERNAL_SHIPPER_REQUEST, "00004", "external_response", true);//comes from confirmation endpoint which is not configured yet on the GBF site
        checkDBValues(CHECK_EXTERNAL_SHIPPER_REQUEST, "00003", "external_response", true);

        // check sent status of kits - add the end of job kit should be set to sent and have a mf barcode
        checkDBValues(CHECK_EXTERNAL_SHIPPER_KIT, "00003", "kit_label", true); // mf barcode
//        checkDBValues(CHECK_EXTERNAL_SHIPPER_KIT, "00004", "kit_label", true); // mf barcode
        checkDBValues(CHECK_EXTERNAL_SHIPPER_KIT, "00003", "tracking_to_id", true); // tracking to id
//        checkDBValues(CHECK_EXTERNAL_SHIPPER_KIT, "00004", "tracking_to_id", true); // tracking to id
        checkDBValues(CHECK_EXTERNAL_SHIPPER_KIT, "00003", "tracking_return_id", true); // tracking return id
//        checkDBValues(CHECK_EXTERNAL_SHIPPER_KIT, "00004", "tracking_return_id", true); // tracking return id
        checkDBValues(CHECK_EXTERNAL_SHIPPER_KIT, "00003", "scan_date", true); // scan date
//        checkDBValues(CHECK_EXTERNAL_SHIPPER_KIT, "00004", "scan_date", true); // scan date
        Assert.assertEquals(String.valueOf(SystemUtil.getLongFromDateString("2018-06-04")), DBTestUtil.getQueryDetail(CHECK_EXTERNAL_SHIPPER_KIT, "00003", "scan_date")); //2018-06-04
        Assert.assertEquals(String.valueOf(SystemUtil.getLongFromDateString("2018-06-04")), DBTestUtil.getQueryDetail(CHECK_EXTERNAL_SHIPPER_KIT, "00004", "scan_date")); //2018-06-04
        checkDBValues(CHECK_EXTERNAL_SHIPPER_KIT, "00003", "scan_by", true); // scan by
//        checkDBValues(CHECK_EXTERNAL_SHIPPER_KIT, "00004", "scan_by", true); // scan by
        checkDBValues(CHECK_EXTERNAL_SHIPPER_KIT, "00003", "kit_complete", true); // kit complete
//        checkDBValues(CHECK_EXTERNAL_SHIPPER_KIT, "00004", "kit_complete", true); // kit complete
}

    public static void cleanDB() {
        //first delete all kit data
        //then delete kit request data
        DBTestUtil.deleteAllKitData("FAKE_PARTICIPANT1");
    }

    public static void uploadKit() throws Exception {
//        String gbfResponse = TestUtil.readFile("gbf/OrderResponse.json");
//        mockDDP.when(
//                request().withPath("/order"))
//                .respond(response().withStatusCode(200).withBody(gbfResponse));

        //upload kits for one type
        String csvContent = TestUtil.readFile("KitUploadPromise.txt");
        HttpResponse response = TestUtil.perform(Request.Post(DSM_BASE_URL + "/ui/" + "kitUpload?realm=" + "testBoston" + "&kitType=TESTBOSTON&userId=26"), csvContent, testUtil.buildAuthHeaders()).returnResponse();
//        Assert. assertEquals(200, response.getStatusLine().getStatusCode());

        // check that kit is in db
        Assert.assertTrue(DBTestUtil.checkIfValueExists(CHECK_EXTERNAL_SHIPPER_REQUEST, "00004"));
        Assert.assertTrue(DBTestUtil.checkIfValueExists(CHECK_EXTERNAL_SHIPPER_REQUEST, "00003"));

        // check that it made 3 kits
        Assert.assertEquals("2", DBTestUtil.getQueryDetail(CHECK_EXTERNAL_SHIPPER_REQUEST_COUNT, "00004", "count(*)"));
        Assert.assertEquals("2", DBTestUtil.getQueryDetail(CHECK_EXTERNAL_SHIPPER_REQUEST_COUNT, "00003", "count(*)"));
    }

    public static void createJob(Scheduler scheduler) throws Exception {
        //create job
        JobDetail job = JobBuilder.newJob(ExternalShipperJob.class)
                .withIdentity("CHECK_EXTERNAL_SHIPPER", BasicTriggerListener.NO_CONCURRENCY_GROUP).build();

        job.getJobDataMap().put(DSMServer.ADDITIONAL_CRON_EXPRESSION, "0 0/4 * ? * * *"); // every 4 min

        //create trigger
        TriggerKey triggerKey = new TriggerKey("CHECK_EXTERNAL_SHIPPER" + "_TRIGGER", "DDP");
        CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey).withSchedule(cronSchedule("0 0/2 * ? * * *")).build(); // every 2 min

        //add job
        scheduler.scheduleJob(job, trigger);

        //add listener for all triggers
        scheduler.getListenerManager().addTriggerListener(new ExternalShipperTriggerListener(), KeyMatcher.keyEquals(triggerKey));
        scheduler.start();
    }

    public static void checkDBValues(@NonNull String query, @NonNull String value, @NonNull String returnColumn, boolean checkIsNotBlank) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            dbVals.resultValue = null;
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, value);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        if (checkIsNotBlank) {
                            Assert.assertTrue(StringUtils.isNotBlank(rs.getString(returnColumn)));
                            rs.getString(returnColumn);
                            rs.getString("dsm_kit_request_id");
                        }
                    }
                }
            }
            catch (Exception e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getQueryDetail ", results.resultException);
        }
    }
}
