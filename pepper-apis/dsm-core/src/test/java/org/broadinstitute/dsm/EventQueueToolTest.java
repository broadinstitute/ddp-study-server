package org.broadinstitute.dsm;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import lombok.NonNull;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.db.ParticipantEvent;
import org.broadinstitute.dsm.model.KitDDPNotification;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.util.DBTestUtil;
import org.broadinstitute.dsm.util.EventUtil;
import org.broadinstitute.dsm.util.TestUtil;
import org.broadinstitute.dsm.util.tools.EventQueueTool;
import org.broadinstitute.dsm.util.tools.util.DBUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collection;

public class EventQueueToolTest {

    private static final Logger logger = LoggerFactory.getLogger(EventQueueToolTest.class);

    private static Config cfg;
    private static EventUtil eventUtil;
    private static String INSTANCE_ID;

    @BeforeClass
    public static void first() {
        cfg = ConfigFactory.load();
        //secrets from vault in a config file
        cfg = cfg.withFallback(ConfigFactory.parseFile(new File(System.getenv("TEST_CONFIG_FILE"))));
        cfg = cfg.withValue("errorAlert.recipientAddress", ConfigValueFactory.fromAnyRef(""));

        if (!cfg.getString("portal.environment").startsWith("Local")) {
            throw new RuntimeException("Not local environment");
        }

        if (!cfg.getString("portal.dbUrl").contains("local")) {
            throw new RuntimeException("Not your test db");
        }

        TransactionWrapper.configureSslProperties(cfg.getString("portal.dbSslKeyStore"),
                cfg.getString("portal.dbSslKeyStorePwd"),
                cfg.getString("portal.dbSslTrustStore"),
                cfg.getString("portal.dbSslTrustStorePwd"));
        TransactionWrapper.reset(TestUtil.UNIT_TEST);
        TransactionWrapper.init(cfg.getInt("portal.maxConnections"), cfg.getString("portal.dbUrl"), cfg, false);

        DBTestUtil.executeQuery("UPDATE ddp_instance set is_active = 1 where instance_name = \"" + TestHelper.TEST_DDP + "\"");
        INSTANCE_ID = DBTestUtil.getQueryDetail(DBUtil.GET_REALM_QUERY, TestHelper.TEST_DDP, TestHelper.DDP_INSTANCE_ID);
        //delete second reminder
        if (DBTestUtil.checkIfValueExists("SELECT * from event_type where ddp_instance_id = " + INSTANCE_ID + " AND event_name = ?", "BLOOD_SENT_2WK")) {
            DBTestUtil.executeQuery("DELETE FROM event_type WHERE ddp_instance_id = " + INSTANCE_ID + " AND event_name = \"BLOOD_SENT_2WK\"");
        }

        //add kits which would need a blood reminder email
        addSentKit("_skipReminder1");
        addSentKit("_skipReminder2");
        addSentKit("_skipReminder3");
        addSentKit("_skipReminder4");
        addSentKit("_skipReminder5");
        addSentKit("_skipReminder6");
        addSentKit("_skipReminder7");
        addSentKit("_skipReminder8");
        TransactionWrapper.reset(TestUtil.UNIT_TEST);

        eventUtil = new EventUtil();
        logger.info("Finished setting up system");
    }

    @AfterClass
    public static void last() {
        logger.info("Removing test cases");
        TransactionWrapper.reset(TestUtil.UNIT_TEST);

        TransactionWrapper.init(cfg.getInt(ApplicationConfigConstants.DSM_DB_MAX_CONNECTIONS),
                cfg.getString(ApplicationConfigConstants.DSM_DB_URL), cfg, false);

        DBTestUtil.executeQuery("UPDATE ddp_instance set is_active = 0 where instance_name = \"" + TestHelper.TEST_DDP + "\"");

        //delete kits again
        DBTestUtil.deleteAllKitData(TestHelper.FAKE_DDP_PARTICIPANT_ID + "_skipReminder1");
        DBTestUtil.deleteAllKitData(TestHelper.FAKE_DDP_PARTICIPANT_ID + "_skipReminder2");
        DBTestUtil.deleteAllKitData(TestHelper.FAKE_DDP_PARTICIPANT_ID + "_skipReminder3");
        DBTestUtil.deleteAllKitData(TestHelper.FAKE_DDP_PARTICIPANT_ID + "_skipReminder4");
        DBTestUtil.deleteAllKitData(TestHelper.FAKE_DDP_PARTICIPANT_ID + "_skipReminder5");
        DBTestUtil.deleteAllKitData(TestHelper.FAKE_DDP_PARTICIPANT_ID + "_skipReminder6");
        DBTestUtil.deleteAllKitData(TestHelper.FAKE_DDP_PARTICIPANT_ID + "_skipReminder7");
        DBTestUtil.deleteAllKitData(TestHelper.FAKE_DDP_PARTICIPANT_ID + "_skipReminder8");

        //add second reminder
        if (!DBTestUtil.checkIfValueExists("SELECT * from event_type where ddp_instance_id = " + INSTANCE_ID + " AND event_name = ?", "BLOOD_SENT_2WK")) {
            DBTestUtil.executeQuery("INSERT INTO event_type set ddp_instance_id = " + INSTANCE_ID + ", event_name=\"BLOOD_SENT_2WK\", event_description=\"Blood kit - reminder email - 2 WKS\", kit_type_id=\"2\", event_type=\"REMINDER\", hours=\"336\"");
        }

        TransactionWrapper.reset(TestUtil.UNIT_TEST);
    }

    @Test
    public void skipEvent() {
        String eventType = "BLOOD_SENT_4WK";
        //call tool to add kits to ddp_participant_event table
        EventQueueTool.argumentsForTesting("config/test-config.conf", TestHelper.TEST_DDP, eventType);
        EventQueueTool.littleMain();

        //assert that kits are entered in ddp_participant_event
        Collection<String> participantEvents = ParticipantEvent.getParticipantEvent(TestHelper.FAKE_DDP_PARTICIPANT_ID + "_skipReminder1", INSTANCE_ID);
        Assert.assertTrue(participantEvents.contains(eventType));
        participantEvents = ParticipantEvent.getParticipantEvent(TestHelper.FAKE_DDP_PARTICIPANT_ID + "_skipReminder2", INSTANCE_ID);
        Assert.assertTrue(participantEvents.contains(eventType));
        participantEvents = ParticipantEvent.getParticipantEvent(TestHelper.FAKE_DDP_PARTICIPANT_ID + "_skipReminder3", INSTANCE_ID);
        Assert.assertTrue(participantEvents.contains(eventType));
        participantEvents = ParticipantEvent.getParticipantEvent(TestHelper.FAKE_DDP_PARTICIPANT_ID + "_skipReminder4", INSTANCE_ID);
        Assert.assertTrue(participantEvents.contains(eventType));
        participantEvents = ParticipantEvent.getParticipantEvent(TestHelper.FAKE_DDP_PARTICIPANT_ID + "_skipReminder5", INSTANCE_ID);
        Assert.assertTrue(participantEvents.contains(eventType));
        participantEvents = ParticipantEvent.getParticipantEvent(TestHelper.FAKE_DDP_PARTICIPANT_ID + "_skipReminder6", INSTANCE_ID);
        Assert.assertTrue(participantEvents.contains(eventType));
        participantEvents = ParticipantEvent.getParticipantEvent(TestHelper.FAKE_DDP_PARTICIPANT_ID + "_skipReminder7", INSTANCE_ID);
        Assert.assertTrue(participantEvents.contains(eventType));
        participantEvents = ParticipantEvent.getParticipantEvent(TestHelper.FAKE_DDP_PARTICIPANT_ID + "_skipReminder8", INSTANCE_ID);
        Assert.assertTrue(participantEvents.contains(eventType));

        //check how many kits eventUtil would find to trigger
        Collection<KitDDPNotification> kitDDPNotifications = eventUtil.getKitsNotReceived();
        Assert.assertEquals(0, kitDDPNotifications.size());
    }

    private static void addSentKit(@NonNull String suffix) {
        DBTestUtil.insertLatestKitRequest(cfg.getString("portal.insertKitRequest"), cfg.getString("portal.insertKit"),
                suffix, 2, INSTANCE_ID);
        DBTestUtil.setKitToSent("FAKE_SPK_UUID" + suffix, "FAKE_DSM_LABEL_UID" + suffix, System.currentTimeMillis() - (18 * DBTestUtil.WEEK));
    }
}