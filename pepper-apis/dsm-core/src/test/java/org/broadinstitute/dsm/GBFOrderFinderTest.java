package org.broadinstitute.dsm;

import static org.broadinstitute.dsm.db.KitRequestShipping.markOrderTransmittedAt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Date;

import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.model.gbf.GBFOrderFinder;
import org.broadinstitute.dsm.model.gbf.SimpleKitOrder;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GBFOrderFinderTest extends TestHelper {

    private static final Logger logger = LoggerFactory.getLogger(GBFOrderFinderTest.class);

    private static final String TEST_STUDY = "testboston";

    private static final String TEST_PARTICIPANT_GUID = "4ECA6IHNZT4S13NSOEMT";

    private static final String TEST_PREFIX = "GBF_TESTING_X";

    private static final int MAX_DAY_WAIT = 30;

    private static final String UPS_STATUS_DATE_FORMAT = "yyyyMMdd HHmmss";

    private static final String MARK_KIT_DELIVERED_AT = "update ddp_kit\n" +
            "set\n" +
            "ups_tracking_status = 'D Fake Delivered',\n" +
            "ups_tracking_date = ?\n" +
            "where\n" +
            "dsm_kit_request_id in (select req.dsm_kit_request_id from ddp_kit_request req where req.external_order_number = ?)";

    private static final String MARK_KIT_RETURNED = "update ddp_kit\n" +
            "set\n" +
            "ups_return_status = 'D Fake Delivered'\n" +
            "where\n" +
            "dsm_kit_request_id in (select req.dsm_kit_request_id from ddp_kit_request req where req.external_order_number = ?)";

    private static final String INSERT_KIT_REQUEST = "insert into ddp_kit_request(ddp_instance_id,kit_type_id,ddp_participant_id,external_order_number,ddp_label)\n" +
            "    (select distinct i.ddp_instance_id, subkit.kit_type_id, ?,?,concat(?,'_',kt.kit_type_name)\n" +
            "     from ddp_instance i,\n" +
            "          ddp_kit_request_settings s,\n" +
            "          sub_kits_settings subkit,\n" +
            "          kit_type kt\n" +
            "     where i.instance_name = ? \n" +
            "       and kt.kit_type_id = subkit.kit_type_id\n" +
            "       and s.ddp_instance_id = i.ddp_instance_id\n" +
            "       and s.ddp_kit_request_settings_id = subkit.ddp_kit_request_settings_id\n" +
            "    )";

    private static final String INSERT_KIT = "insert into ddp_kit(dsm_kit_request_id, kit_label) values (?,?)";

    private static final String DELETE_KITS = "delete from ddp_kit_request\n"+
            "where\n"+
            "external_order_number like ?\n"+
            "and\n"+
            "ddp_instance_id = (select i.ddp_instance_id from ddp_instance i where i.instance_name = ?)";

    private static final String DELETE_KIT_REQUESTS = "delete from ddp_kit\n" +
            "where\n" +
            "dsm_kit_request_id in\n" +
            "(select dsm_kit_request_id from ddp_kit_request\n" +
            "where\n" +
            "external_order_number like ?\n" +
            "and\n" +
            "ddp_instance_id = (select i.ddp_instance_id from ddp_instance i where i.instance_name = ?))\n";

    private static final String SET_TRANSMISSION_DATES = "\n"+
            "update ddp_kit_request set order_transmitted_at = now()\n"+
            "where\n"+
            "order_transmitted_at is null\n"+
            "and\n"+
            "ddp_instance_id = (select i.ddp_instance_id from ddp_instance i where i.instance_name = ?)\n";

    @BeforeClass
    public static void setUp() {
        setupEsClient();
    }

    private String createTestKit(Connection conn) {
        String externalOrderId = TEST_PREFIX + randomStringGenerator(10, true, false, true);

        try (PreparedStatement stmt = conn.prepareStatement(INSERT_KIT_REQUEST, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, TEST_PARTICIPANT_GUID);
            stmt.setString(2, externalOrderId);
            stmt.setString(3, externalOrderId);
            stmt.setString(4, TEST_STUDY);

            int numRowsInserted = stmt.executeUpdate();

            logger.info("Inserted {} rows for test kit request {}", numRowsInserted, externalOrderId);

            ResultSet generatedKeys = stmt.getGeneratedKeys();

            int kitNumber = 1;
            while (generatedKeys.next()) {
                try (PreparedStatement kitInsert = conn.prepareStatement(INSERT_KIT)) {
                    kitInsert.setLong(1, generatedKeys.getLong(1));
                    kitInsert.setString(2, externalOrderId + "_" + kitNumber++);

                    numRowsInserted = kitInsert.executeUpdate();
                    logger.info("Inserted {} rows for test kit {}", numRowsInserted, externalOrderId);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not create test kit " + externalOrderId, e);
        }
        return externalOrderId;
    }

    private void hidePendingKitRequests(Connection conn) {
        // delete any of kits that we added previous for this test
        // mark all kits as ordered
        try  {
            PreparedStatement update = conn.prepareStatement(SET_TRANSMISSION_DATES);
            update.setString(1, TEST_STUDY);
            int numRows = update.executeUpdate();

            logger.info("Updated {} untransmitted kit requests for {}", numRows, TEST_STUDY);

            update = conn.prepareStatement(DELETE_KIT_REQUESTS);
            update.setString(1, TEST_PREFIX + "%");
            update.setString(2, TEST_STUDY);

            numRows = update.executeUpdate();

            logger.info("deleted {} test kit requests", numRows);

            update = conn.prepareStatement(DELETE_KITS);
            update.setString(1, TEST_PREFIX + "%");
            update.setString(2,TEST_STUDY);

            numRows = update.executeUpdate();

            logger.info("deleted {} test kits", numRows);


        } catch (SQLException e) {
            throw new RuntimeException("Could not clean up pending kits and previous test data", e);
        }
    }

    @Test
    public void testFirstKitGetsOrdered() {
        GBFOrderFinder finder = new GBFOrderFinder(null,1,esClient, "participants_structured.testboston.testboston");

        TransactionWrapper.inTransaction(conn -> {
            try {
                hidePendingKitRequests(conn);
                String externalOrderId = createTestKit(conn);
                Collection<SimpleKitOrder> kitsToOrder = finder.findKitsToOrder("testboston", conn);
                Assert.assertEquals(1, kitsToOrder.size());
                SimpleKitOrder newOrder = kitsToOrder.iterator().next();
                Assert.assertEquals(externalOrderId, newOrder.getExternalKitOrderNumber());
            } finally {
                try {
                    conn.rollback();
                } catch (SQLException e) {
                    logger.error("Could not roll back after test");
                }

            }
            return null;
        });
    }

    @Test
    public void testKitNotOrderedWhenNoPreviousKitReturned() {
        GBFOrderFinder finder = new GBFOrderFinder(null,1,esClient, "participants_structured.testboston.testboston");

        TransactionWrapper.inTransaction(conn -> {
            try {
                hidePendingKitRequests(conn);
                String firstKit = createTestKit(conn);
                Collection<SimpleKitOrder> kitsToOrder = finder.findKitsToOrder("testboston", conn);
                Assert.assertEquals(1, kitsToOrder.size());
                String secondKit = createTestKit(conn);
                kitsToOrder = finder.findKitsToOrder("testboston", conn);
                Assert.assertTrue("Should not allow the 2nd kit request to go through while no previous ones have been returned",kitsToOrder.isEmpty());
            } finally {
                try {
                    conn.rollback();
                } catch (SQLException e) {
                    logger.error("Could not roll back after test");
                }

            }
            return null;
        });
    }

    @Test
    public void testDuplicateKitNotOrdered() {
        GBFOrderFinder finder = new GBFOrderFinder(null,1,esClient, "participants_structured.testboston.testboston");

        TransactionWrapper.inTransaction(conn -> {
            try {
                hidePendingKitRequests(conn);
                String firstKit = createTestKit(conn);
                Collection<SimpleKitOrder> kitsToOrder = finder.findKitsToOrder("testboston", conn);
                Assert.assertEquals(1, kitsToOrder.size());
                Assert.assertEquals(firstKit, kitsToOrder.iterator().next().getExternalKitOrderNumber());
                markOrderTransmittedAt(conn, firstKit, Instant.now());
                kitsToOrder = finder.findKitsToOrder("testboston", conn);
                Assert.assertTrue("Should not have found the same order since we just transmitted it",kitsToOrder.isEmpty());
            } finally {
                try {
                    conn.rollback();
                } catch (SQLException e) {
                    logger.error("Could not roll back after test");
                }

            }
            return null;
        });
    }

    @Test
    public void testSubsequentKitOrderGoesThroughWhenPreviousOneIsReturnedBeforeTimeout() {
        GBFOrderFinder finder = new GBFOrderFinder(null,1,esClient, "participants_structured.testboston.testboston");

        TransactionWrapper.inTransaction(conn -> {
            try {
                hidePendingKitRequests(conn);
                String firstKit = createTestKit(conn);
                Collection<SimpleKitOrder> kitsToOrder = finder.findKitsToOrder("testboston", conn);
                Assert.assertEquals(1, kitsToOrder.size());
                Assert.assertEquals(firstKit, kitsToOrder.iterator().next().getExternalKitOrderNumber());
                for (SimpleKitOrder simpleKitOrder : kitsToOrder) {
                    markOrderTransmittedAt(conn, simpleKitOrder.getExternalKitOrderNumber(), Instant.now());
                    markOrderDeliveredToRecipientAt(conn, simpleKitOrder.getExternalKitOrderNumber(), Instant.now());
                    markOrderAsReturned(conn, simpleKitOrder.getExternalKitOrderNumber());
                }
                String secondKit = createTestKit(conn);

                logger.info("Created 2nd kit {}", secondKit);

                kitsToOrder = finder.findKitsToOrder("testboston", conn);


                Assert.assertEquals("Second kit should have gone out since the first one came back on schedule", 1, kitsToOrder.size());
                SimpleKitOrder kitToOrder = kitsToOrder.iterator().next();
                Assert.assertEquals(secondKit, kitToOrder.getExternalKitOrderNumber());
                Assert.assertEquals(TEST_PARTICIPANT_GUID, kitToOrder.getParticipantGuid());
                Assert.assertEquals("Subsequent kit order should have gone through because the previous one was recently returned",1, kitsToOrder.size());

            } finally {
                try {
                    conn.rollback();
                } catch (SQLException e) {
                    logger.error("Could not roll back after test");
                }
            }
            return null;
        });
    }

    @Test
    public void testSubsequentKitOrderIsBlockedThroughWhenPreviousOneIsReturnedAfterTimeout() {
        GBFOrderFinder finder = new GBFOrderFinder(null,1,esClient, "participants_structured.testboston.testboston");

        TransactionWrapper.inTransaction(conn -> {
            try {
                int daysPrior = MAX_DAY_WAIT + 1;
                hidePendingKitRequests(conn);
                String firstKit = createTestKit(conn);
                Collection<SimpleKitOrder> kitsToOrder = finder.findKitsToOrder("testboston", conn);
                for (SimpleKitOrder simpleKitOrder : kitsToOrder) {
                    // if the kit was returned long after delivery, a new kit should not be ordered
                    markOrderDeliveredToRecipientAt(conn, simpleKitOrder.getExternalKitOrderNumber(), Instant.now().minus(daysPrior, ChronoUnit.DAYS));
                    markOrderAsReturned(conn, simpleKitOrder.getExternalKitOrderNumber());
                }
                String secondKit = createTestKit(conn);
                kitsToOrder = finder.findKitsToOrder("testboston", conn);

                Assert.assertTrue("Subsequent kit order should have been blocked since the return was " + daysPrior + " days ago",kitsToOrder.isEmpty());


            } finally {
                try {
                    conn.rollback();
                } catch (SQLException e) {
                    logger.error("Could not roll back after test");
                }

            }
            return null;
        });
    }

    private void markOrderDeliveredToRecipientAt(Connection conn, String kitExternalOrderId, Instant arrivedToRecipientAt) {
        try (PreparedStatement stmt = conn.prepareStatement(MARK_KIT_DELIVERED_AT)) {
            stmt.setString(1, new SimpleDateFormat(UPS_STATUS_DATE_FORMAT).format(Date.from(arrivedToRecipientAt)));
            stmt.setString(2, kitExternalOrderId);

            int numRows = stmt.executeUpdate();

            logger.info("Updated {} rows for {} delivered at {}", numRows, kitExternalOrderId, arrivedToRecipientAt);
        } catch (SQLException e) {
            throw new RuntimeException("Could not mark " + kitExternalOrderId + " as delivered", e);
        }
    }


    private void markOrderAsReturned(Connection conn, String kitExternalOrder) {
        try (PreparedStatement stmt = conn.prepareStatement(MARK_KIT_RETURNED)) {
            stmt.setString(1, kitExternalOrder);

            int numRows = stmt.executeUpdate();
            logger.info("Updated {} rows while marking {} as returned", numRows, kitExternalOrder);
        } catch (SQLException e) {
            throw new RuntimeException("Could not mark " + kitExternalOrder + " as returned", e);
        }
    }
}
