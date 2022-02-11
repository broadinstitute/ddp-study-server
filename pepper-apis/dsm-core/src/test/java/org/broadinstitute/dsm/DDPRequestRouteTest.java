package org.broadinstitute.dsm;

import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.route.KitRequestRoute;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.QueryExtension;
import org.broadinstitute.dsm.util.*;
import org.junit.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class DDPRequestRouteTest extends TestHelper {

    private static final Logger logger = LoggerFactory.getLogger(DDPRequestRouteTest.class);

    private List<String> kitRequests;

    private boolean addedDefaultKitRequest = false;

    private final String test_participant_id = "FAKE_PARTICIPANT";

    private int counter = 0;

    private List<KitRequestShipping> kitRequestTestList;

    @BeforeClass
    public static void first() throws Exception {
        setupDB();

        startMockServer();
        setupUtils();

        logger.info("Setting up stuff");
    }

    @AfterClass
    public static void last() {
        stopMockServer();
        cleanupDB();
    }

    @Before
    public void checkForParticipant() throws Exception {
        kitRequests = new ArrayList<>();
        inTransaction((conn) -> {
            try (PreparedStatement stmt = conn.prepareStatement(KitRequestShipping.SQL_SELECT_KIT_REQUEST.concat(QueryExtension.BY_REALM_AND_TYPE).concat(" and not (kit.kit_complete <=> 1) and not (kit.error <=> 1) and label_date is null"))) {
                stmt.setString(1, TEST_DDP);
                stmt.setString(2, "SALIVA");
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        kitRequests.add(rs.getString(DBConstants.DDP_PARTICIPANT_ID));
                    }
                }
            }
            catch (SQLException e) {
                throw new RuntimeException("checkForParticipant ", e);
            }
            return null;
        });
        String message = TestUtil.readFile("ddpResponses/ParticipantsWithId.json");
        logger.info("Response from Participants.json: " + message);
        kitRequestTestList = new ArrayList<>();
        if (kitRequests.isEmpty()) {
            //add Test Participant ID
            logger.info("No KitRequests in ddp_kit_requests going to add one for testing");
            SimpleResult results = inTransaction((conn) -> {
                SimpleResult dbVals = new SimpleResult(0);
                try (PreparedStatement stmt = conn.prepareStatement(cfg.getString("portal.insertKitRequest"))) {
                    stmt.setString(1, INSTANCE_ID);
                    stmt.setString(2, "FAKE_KIT_REQUEST");
                    stmt.setInt(3, 1);
                    stmt.setString(4, test_participant_id);
                    stmt.setString(5, "FAKE_BSP_COLL_ID");
                    stmt.setString(6, "FAKE_BSP_SAM_ID");
                    stmt.setString(7, "FAKE_DSM_LABEL_UID");
                    stmt.setString(8, "TEST");
                    stmt.setLong(9, System.currentTimeMillis());
                    stmt.setObject(10, null);
                    dbVals.resultValue = stmt.executeUpdate();
                }
                catch (SQLException e) {
                    dbVals.resultException = e;
                }
                return dbVals;
            });

            if (results.resultException != null || (Integer) results.resultValue != 1) {
                throw new RuntimeException("Error getting list of kitRequests for test ddp ", results.resultException);
            }

            mockDDP.when(request().withPath("/ddp/participants/" + test_participant_id)).respond(
                    response().withStatusCode(200).withBody(message.replaceAll("%1", test_participant_id).replaceAll("%2", ""))
            );
            kitRequestTestList.add(new KitRequestShipping(test_participant_id, "TestProject_2", null, "FAKE_DSM_LABEL_UID", TEST_DDP,
                    "SALIVA", 1L, 1L, "https://easypost-files.s3-us-west-2.amazonaws" +
                    ".com/files/postage_label/20200214/8240f1b66535494a82b1ec0d566c3f0f.png",
                    "", "794685038506", "9405536897846100551129", "https://track.easypost.com/djE6dHJrXzY4NGJmYzU3ZjM5OTQ1Zjg5MjEzOGRmMWVmMjI1NWZl",
                    null, 12L, false, "", 12L, null, 12L, "so what", "mf_testLabel", false, "shp_f470591c3fb441a68dbb9b76ecf3bb3d",
                    12L, null, "44445", false, "NOT FOUND", null, null, null, null, 0L, false, "STANDALONE", null, null, null));
            addedDefaultKitRequest = true;
            counter = 1;
        }
        else {
            // fill mockAngio with requests
            for (String participant_id : kitRequests) {
                logger.info(message.replaceAll("%1", participant_id).replaceAll("%2", Integer.toString(counter)));
                mockDDP.when(request().withPath("/ddp/participants/" + participant_id)).respond(
                        response().withStatusCode(200).withBody(message.replaceAll("%1", participant_id).replaceAll("%2", Integer.toString(counter)))
                );
                kitRequestTestList.add(new KitRequestShipping(participant_id, "TestProject_2", null, "FAKE_DSM_LABEL_UID" + counter, TEST_DDP,
                        "SALIVA", 1L, 1L, "https://easypost-files.s3-us-west-2.amazonaws.com/files/postage_label/20200214/8240f1b66535494a82b1ec0d566c3f0f.png",
                        "", "794685038506", "9405536897846100551129", "https://track.easypost.com/djE6dHJrXzY4NGJmYzU3ZjM5OTQ1Zjg5MjEzOGRmMWVmMjI1NWZl",
                        null, 12L, false, "", 12L, null, 12L, "so what", "mf_testLabel", false, "shp_f470591c3fb441a68dbb9b76ecf3bb3d",
                        12L, null, "44445", false, "NOT FOUND", null, null, null, null, 0L ,false, null, null, null, null));
                counter++;
            }
        }
    }

    @Test
    public void readKitRequest() {
        String realm = TEST_DDP;
        try {
            KitRequestRoute route = new KitRequestRoute();
            inTransaction((conn) -> {
                try (PreparedStatement stmt = conn.prepareStatement(DDPInstance.SQL_SELECT_ALL_ACTIVE_REALMS + QueryExtension.BY_INSTANCE_NAME)) {
                    stmt.setString(1, realm);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            List<KitRequestShipping> kitRequestList = KitRequestShipping.getKitRequestsByRealm(realm, "uploaded", "SALIVA");

                            Assert.assertEquals(counter, kitRequestList.size());

                            logger.info("result of ddp_kit_request with name and address of participants:");
                            int x = 0;
                            kitRequestList.sort(new Comparator<KitRequestShipping>() {
                                @Override
                                public int compare(KitRequestShipping o1, KitRequestShipping o2) {
                                    return (int) (o1.getDsmKitId() - o2.getDsmKitId());
                                }
                            });
                            for (KitRequestShipping kit : kitRequestList) {
                                Assert.assertEquals(kit.getParticipantId(), kitRequestTestList.get(x).getParticipantId());
                                x++;
                            }
                        }
                    }
                }
                catch (SQLException e) {
                    throw new RuntimeException("test_readKitRequest ", e);
                }
                return null;
            });
        }
        catch (Exception e) {
            logger.error("Starting up the blindTrustEverythingExecutor");
        }
    }

    @After
    public void cleanTestSettings() {
        if (addedDefaultKitRequest) {
            DBTestUtil.deleteAllKitData(test_participant_id);
        }
    }
}
