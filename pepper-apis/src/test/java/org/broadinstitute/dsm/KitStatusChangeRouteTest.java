package org.broadinstitute.dsm;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.TestHelper;
import org.broadinstitute.dsm.route.KitStatusChangeRoute;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.DBTestUtil;
import org.broadinstitute.dsm.util.TestUtil;
import org.broadinstitute.dsm.util.tools.UpdateReceivedDateTool;
import org.broadinstitute.dsm.util.tools.util.DBUtil;
import org.junit.*;

import java.util.ArrayList;
import java.util.List;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

public class KitStatusChangeRouteTest extends TestHelper {

    private static final String CHECK_KITLABEL = "select count(*) as count from ddp_kit_request request, ddp_kit kit where request.dsm_kit_request_id = kit.dsm_kit_request_id and request.ddp_kit_request_id = ? and kit_label = ?";
    private static final String CHECK_TRACKING = "select count(*) as count from ddp_kit_tracking where kit_label = ?";

    private static KitStatusChangeRoute route;

    @BeforeClass
    public static void first() {
        setupDB();

        route = new KitStatusChangeRoute();
    }

    @AfterClass
    public static void afterwards() {
        DBTestUtil.deleteAllKitData(FAKE_DDP_PARTICIPANT_ID);
        DBTestUtil.deleteAllKitData(FAKE_DDP_PARTICIPANT_ID + "_1");
        DBTestUtil.deleteAllKitData(FAKE_DDP_PARTICIPANT_ID + "_2");
        DBTestUtil.executeQuery("delete from ddp_kit_tracking where kit_label = ? ", FAKE_DSM_LABEL_UID + "_3");
        DBTestUtil.deleteAllKitData(FAKE_DDP_PARTICIPANT_ID + "_3");
        DBTestUtil.deleteAllKitData(FAKE_DDP_PARTICIPANT_ID + "_4");
        DBTestUtil.deleteAllKitData(FAKE_DDP_PARTICIPANT_ID + "_5");

        cleanupDB();
    }

    @Test
    public void getBSPKitQuery() throws Exception {
        DBTestUtil.insertLatestKitRequest(cfg.getString("portal.insertKitRequest"), cfg.getString("portal.insertKit"),
                "_1", 1, DBTestUtil.getQueryDetail(DBUtil.GET_REALM_QUERY, TEST_DDP, DDP_INSTANCE_ID));
        DBTestUtil.insertLatestKitRequest(cfg.getString("portal.insertKitRequest"), cfg.getString("portal.insertKit"),
                "_2", 1, DBTestUtil.getQueryDetail(DBUtil.GET_REALM_QUERY, TEST_DDP, DDP_INSTANCE_ID));

        String json = TestUtil.readFile("FinalScanPayload.json");
        JsonArray scans = (JsonArray) (new JsonParser().parse(json));
        List<KitStatusChangeRoute.ScanError> scanErrorList = new ArrayList<>();
        route.updateKits("finalScan", scans, System.currentTimeMillis(), scanErrorList, "3");

        List<String> strings = new ArrayList<>();
        strings.add(FAKE_LATEST_KIT + "_1");
        strings.add("spk-FAKE-KITLABEL-1");
        String count = DBTestUtil.getStringFromQuery(CHECK_KITLABEL, strings, "count");
        //check if kitlabel was added to dsmlabel
        Assert.assertEquals("1", count);

        strings = new ArrayList<>();
        strings.add(FAKE_LATEST_KIT + "_2");
        strings.add("spk-FAKE-KITLABEL-2");
        count = DBTestUtil.getStringFromQuery(CHECK_KITLABEL, strings, "count");
        //check if kitlabel was added to dsmlabel
        Assert.assertEquals("1", count);

        TransactionWrapper.reset(TestUtil.UNIT_TEST);
        //update receive_date per tool
        UpdateReceivedDateTool.argumentsForTesting("config/test-config.conf", "receivedUpdate.txt");
        UpdateReceivedDateTool.littleMain();

        //check receive_dates
        inTransaction((conn) -> {
            try {
                String receiveDate = DBUtil.checkNotReceived(conn, UpdateReceivedDateTool.SELECT_KIT_RECEIVED_QUERY, "spk-FAKE-KITLABEL-1", DBConstants.DSM_RECEIVE_DATE);
                Assert.assertEquals(String.valueOf(DBUtil.getLong("11/03/2016")), receiveDate);
                receiveDate = DBUtil.checkNotReceived(conn, UpdateReceivedDateTool.SELECT_KIT_RECEIVED_QUERY, "spk-FAKE-KITLABEL-2", DBConstants.DSM_RECEIVE_DATE);
                Assert.assertEquals(String.valueOf(DBUtil.getLong("05/24/2017")), receiveDate);
            }
            catch (Exception e) {
                Assert.fail();
            }
            return null;
        });
    }

    @Test
    public void trackingScan() throws Exception {
        try {
            DBTestUtil.insertLatestKitRequest(cfg.getString("portal.insertKitRequest"), cfg.getString("portal.insertKit"),
                    "_3", 2, DBTestUtil.getQueryDetail(DBUtil.GET_REALM_QUERY, TEST_DDP, DDP_INSTANCE_ID));
        }
        catch (Exception e) {
            //in case kit is not already in db
        }

        String json = TestUtil.readFile("TrackingScanPayload.json");
        JsonArray scans = (JsonArray) (new JsonParser().parse(json));
        List<KitStatusChangeRoute.ScanError> scanErrorList = new ArrayList<>();
        route.updateKits("trackingScan", scans, System.currentTimeMillis(), scanErrorList, "3");

        List<String> strings = new ArrayList<>();
        strings.add(FAKE_DSM_LABEL_UID + "_3");
        String count = DBTestUtil.getStringFromQuery(CHECK_TRACKING, strings, "count");
        //check if kitlabel was added to tracking table
        Assert.assertEquals("1", count);

        route.updateKits("finalScan", scans, System.currentTimeMillis(), scanErrorList, "3");

        strings = new ArrayList<>();
        strings.add(FAKE_LATEST_KIT + "_3");
        strings.add(FAKE_DSM_LABEL_UID + "_3");
        count = DBTestUtil.getStringFromQuery(CHECK_KITLABEL, strings, "count");
        //check if kitlabel was added to dsmlabel
        Assert.assertEquals("1", count);
    }

    @Test
    public void noTrackingScan() throws Exception {
        DBTestUtil.insertLatestKitRequest(cfg.getString("portal.insertKitRequest"), cfg.getString("portal.insertKit"),
                "_4", 2, DBTestUtil.getQueryDetail(DBUtil.GET_REALM_QUERY, TEST_DDP, DDP_INSTANCE_ID));

        String json = TestUtil.readFile("TrackingScanPayload.json");
        JsonArray scans = (JsonArray) (new JsonParser().parse(json));
        List<KitStatusChangeRoute.ScanError> scanErrorList = new ArrayList<>();
        route.updateKits("finalScan", scans, System.currentTimeMillis(), scanErrorList, "3");

        List<String> strings = new ArrayList<>();
        strings.add(FAKE_LATEST_KIT + "_4");
        strings.add(FAKE_DSM_LABEL_UID + "_4");
        String count = DBTestUtil.getStringFromQuery(CHECK_KITLABEL, strings, "count");
        //check that kit label was not updated as send, because tracking scan was missing
        Assert.assertEquals("0", count);
        Assert.assertFalse(scanErrorList.isEmpty());
    }

    @Test
    public void doubleTrackingScan() throws Exception {
        try {
            DBTestUtil.insertLatestKitRequest(cfg.getString("portal.insertKitRequest"), cfg.getString("portal.insertKit"),
                    "_3", 2, DBTestUtil.getQueryDetail(DBUtil.GET_REALM_QUERY, TEST_DDP, DDP_INSTANCE_ID));
        }
        catch (Exception e) {
            //in case kit is not already in db
        }
        String json = TestUtil.readFile("TrackingScanPayload.json");
        JsonArray scans = (JsonArray) (new JsonParser().parse(json));
        List<KitStatusChangeRoute.ScanError> scanErrorList = new ArrayList<>();
        route.updateKits("trackingScan", scans, System.currentTimeMillis(), scanErrorList, "3");

        List<String> strings = new ArrayList<>();
        strings.add(FAKE_DSM_LABEL_UID + "_3");
        String count = DBTestUtil.getStringFromQuery(CHECK_TRACKING, strings, "count");
        //check if kitlabel was added to tracking table
        Assert.assertEquals("1", count);

        scanErrorList = new ArrayList<>();
        route.updateKits("trackingScan", scans, System.currentTimeMillis(), scanErrorList, "3");
    }
}
