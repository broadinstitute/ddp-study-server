package org.broadinstitute.dsm;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.ddp.util.BasicTriggerListener;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.LatestKitRequest;
import org.broadinstitute.dsm.jobs.DDPRequestJob;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.*;
import org.broadinstitute.dsm.util.tools.util.DBUtil;
import org.broadinstitute.dsm.util.triggerListener.DDPRequestTriggerListener;
import org.jruby.embed.ScriptingContainer;
import org.junit.*;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;

public class QuartzDDPRequestTest extends TestHelper {

    private static final Logger logger = LoggerFactory.getLogger(QuartzDDPRequestTest.class);

    private static String maxParticipant;
    private static List<String> switchedOffDDPS;

    @BeforeClass
    public static void first() throws Exception {
        switchedOffDDPS = new ArrayList<>();
        setupDB(true);
        startMockServer();
        setupUtils();
        List<DDPInstance> instances = DDPInstance.getDDPInstanceListWithRole(DBConstants.PDF_DOWNLOAD);
        for (DDPInstance ddpInstance : instances) {
            if (StringUtils.isNotBlank(ddpInstance.getBaseUrl()) && ddpInstance.getBaseUrl().indexOf("localhost") == -1) {
                DBTestUtil.executeQuery("UPDATE ddp_instance set is_active = 0 where instance_name = \"" + ddpInstance.getName() + "\"");
                switchedOffDDPS.add(ddpInstance.getName());
            }
        }
    }

    @AfterClass
    public static void last() {
        stopMockServer();
        stopDSMServer();
        cleanDB();
        cleanupDB();
        if (!switchedOffDDPS.isEmpty()) {
            for (String ddp : switchedOffDDPS) {
                DBTestUtil.executeQuery("UPDATE ddp_instance set is_active = 1 where instance_name = \"" + ddp + "\"");
            }
        }
    }

    public static void cleanDB() {
        //first delete all kit data
        //then delete kit request data
        DBTestUtil.deleteAllKitData("FAKE_PARTICIPANT1");
        DBTestUtil.deleteAllKitData("FAKE_PARTICIPANT2");
        DBTestUtil.deleteAllKitData("FAKE_PARTICIPANT3");
        DBTestUtil.deleteAllKitData("FAKE_PARTICIPANT4");
        DBTestUtil.deleteAllKitData("FAKE_PARTICIPANT5");

        DBTestUtil.deleteAllParticipantData("66666666");
        DBTestUtil.deleteAllParticipantData("75000000");
        DBTestUtil.deleteAllParticipantData("80000000");
        List strings = new ArrayList<>();
        strings.add(maxParticipant);
        strings.add(DBTestUtil.getQueryDetail(DBUtil.GET_REALM_QUERY, TEST_DDP, "ddp_instance_id"));
        DBTestUtil.executeQueryWStrings("update bookmark set value = ? where instance = ? ", strings);

    }

    @Test
    public void quartzKitRequestTest() throws Exception {
        String roleId = DBTestUtil.getQueryDetail("SELECT * from instance_role where name = ?", "pdf_download_consent", "instance_role_id");
        String secondRoleId = DBTestUtil.getQueryDetail("SELECT * from instance_role where name = ?", "pdf_download_release", "instance_role_id");
        try {
            DBTestUtil.executeQuery("INSERT INTO ddp_instance_role SET ddp_instance_id = " + INSTANCE_ID + ", instance_role_id = " + roleId);
            DBTestUtil.executeQuery("INSERT INTO ddp_instance_role SET ddp_instance_id = " + INSTANCE_ID + ", instance_role_id = " + secondRoleId);
            settingUpMock();

            JobDetail job = JobBuilder.newJob(DDPRequestJob.class)
                    .withIdentity("DDPREQUEST_JOB", BasicTriggerListener.NO_CONCURRENCY_GROUP + ".DSM").build();

            if (job != null) {
                //pass parameters to JobDataMap for JobDetail
                job.getJobDataMap().put(DSMServer.KIT_UTIL, kitUtil);
                job.getJobDataMap().put(DSMServer.DDP_UTIL, ddpRequestUtil);

                Object receiver = null;
                ScriptingContainer container = null;
                try {
                    container = new ScriptingContainer();
                    container.getLoadPaths().add(container.getClassLoader().getResource("encryptorGem").getPath());
                    container.runScriptlet("require 'encryptor'");
                    receiver = container.runScriptlet(DSMServer.SCRIPT);
                }
                catch (Exception e) {
                    logger.error("Couldn't setup ruby for MBC decryption");
                }
                job.getJobDataMap().put(DSMServer.CONTAINER, container);
                job.getJobDataMap().put(DSMServer.RECEIVER, receiver);


                //create trigger
                TriggerKey triggerKey = new TriggerKey("TEST_DDPREQUEST_JOB_TRIGGER", "DDP");
                SimpleTrigger trigger = TriggerBuilder.newTrigger()
                        .withIdentity(triggerKey).withSchedule(simpleSchedule()
                                .withIntervalInSeconds(20)
                                .withRepeatCount(4)).build();

                //add job
                Scheduler scheduler = new StdSchedulerFactory().getScheduler();
                //add listener for all triggers
                scheduler.getListenerManager().addTriggerListener(new DDPRequestTriggerListener());
                scheduler.start();
                scheduler.scheduleJob(job, trigger);

                // wait for trigger to finish repeats
                try {
                    Thread.sleep(180L * 1000L);//2 min
                    logger.info("Enough testing going to stop scheduler ...");
                    scheduler.shutdown(true);
                }
                catch (Exception e) {
                    logger.error("something went wrong, while waiting for quartz jon to finish...", e);
                    throw new RuntimeException("something went wrong, while waiting for quartz jon to finish...", e);
                }
                nowCheckDBForKitRequests();
                nowCheckDBForMR();
            }
        }
        catch (Exception e) {
            logger.error(e.getMessage());
            Assert.fail();
        }
        finally {
            DBTestUtil.executeQuery("DELETE FROM ddp_instance_role WHERE ddp_instance_id = " + INSTANCE_ID + " and instance_role_id = " + roleId);
            DBTestUtil.executeQuery("DELETE FROM ddp_instance_role WHERE ddp_instance_id = " + INSTANCE_ID + " and instance_role_id = " + secondRoleId);
        }
    }

    private void settingUpMock() throws Exception {
        String last = "";
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try {
                try (PreparedStatement stmt = conn.prepareStatement(LatestKitRequest.SQL_SELECT_LATEST_KIT_REQUESTS + " and site.instance_name = '" + TEST_DDP + "'")) {
                    stmt.setString(1, DBConstants.HAS_KIT_REQUEST_ENDPOINTS);
                    stmt.setString(2, DBConstants.PDF_DOWNLOAD_CONSENT);
                    stmt.setString(3, DBConstants.PDF_DOWNLOAD_RELEASE);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            dbVals.resultValue = rs.getString(DBConstants.LAST_KIT);
                        }
                    }
                    catch (SQLException e) {
                        throw new RuntimeException("Error getting list of kitRequests for mock ddp", e);
                    }
                }

            }
            catch (Exception e) {
                throw new RuntimeException("stopDSMServer ", e);
            }
            return dbVals;
        });
        last = (String) results.resultValue;
        String message = TestUtil.readFile("ddpResponses/forQuartzJob/Kitrequests_1.json");
        // Mock Angio Server
        if (StringUtils.isNotBlank(last)) {
            mockDDP.when(
                    request().withPath("/ddp/kitrequests/" + last))
                    .respond(response().withStatusCode(200)
                            .withBody(message));
        }
        else {
            mockDDP.when(
                    request().withPath("/ddp/kitrequests"))
                    .respond(response().withStatusCode(200)
                            .withBody(message));
        }
        String message2 = TestUtil.readFile("ddpResponses/forQuartzJob/Kitrequests_2.json");
        mockDDP.when(
                request().withPath("/ddp/kitrequests/fake0002-d034-4d8c-acbf-00391050115e"))
                .respond(response().withStatusCode(200)
                        .withBody(message2));
        String message3 = TestUtil.readFile("ddpResponses/forQuartzJob/Kitrequests_3.json");
        mockDDP.when(
                request().withPath("/ddp/kitrequests/fake0003-d034-4d8c-acbf-00391050115e"))
                .respond(response().withStatusCode(200)
                        .withBody(message3));
        String message4 = TestUtil.readFile("ddpResponses/forQuartzJob/Kitrequests_4.json");
        mockDDP.when(
                request().withPath("/ddp/kitrequests/fake0005-d034-4d8c-acbf-00391050115e"))
                .respond(response().withStatusCode(200)
                        .withBody(message4));// no new participants are added --> empty json !
        String messageParticipant1 = TestUtil.readFile("ddpResponses/ParticipantsWithId.json");
        mockDDP.when(
                request().withPath("/ddp/participants/FAKE_PARTICIPANT1"))
                .respond(response().withStatusCode(200)
                        .withBody(messageParticipant1));
        mockDDP.when(
                request().withPath("/ddp/participants/FAKE_PARTICIPANT2"))
                .respond(response().withStatusCode(200)
                        .withBody(messageParticipant1));
        mockDDP.when(
                request().withPath("/ddp/participants/FAKE_PARTICIPANT3"))
                .respond(response().withStatusCode(200)
                        .withBody(messageParticipant1));
        mockDDP.when(
                request().withPath("/ddp/participants/FAKE_PARTICIPANT4"))
                .respond(response().withStatusCode(200)
                        .withBody(messageParticipant1));
        mockDDP.when(
                request().withPath("/ddp/participants/FAKE_PARTICIPANT5"))
                .respond(response().withStatusCode(200)
                        .withBody(messageParticipant1));

        //participantRoute
        maxParticipant = DBTestUtil.getQueryDetail(DBTestUtil.SELECT_MAXPARTICIPANT,
                DBTestUtil.getQueryDetail(DBUtil.GET_REALM_QUERY, TEST_DDP, "ddp_instance_id"), "value");
        String institutionRequest1 = TestUtil.readFile("ddpResponses/forQuartzJob/Institutionrequests_1.json");
        mockDDP.when(
                request().withPath("/ddp/institutionrequests/" + maxParticipant))
                .respond(response().withStatusCode(200).withBody(institutionRequest1));
        String institutionRequest2 = TestUtil.readFile("ddpResponses/forQuartzJob/Institutionrequests_2.json");
        mockDDP.when(
                request().withPath("/ddp/institutionrequests/66666666"))
                .respond(response().withStatusCode(200).withBody(institutionRequest2));
        String institutionRequest3 = TestUtil.readFile("ddpResponses/forQuartzJob/Institutionrequests_3.json");
        mockDDP.when(
                request().withPath("/ddp/institutionrequests/70000000"))
                .respond(response().withStatusCode(200).withBody(institutionRequest3));
        mockDDP.when(
                request().withPath("/ddp/institutionrequests/80000000"))
                .respond(response().withStatusCode(200).withBody("[]"));

        File file = TestUtil.getResouresFile("Consent.pdf");
        byte[] bytes = Files.readAllBytes(Paths.get(file.getPath()));
        mockDDP.when(
                request().withPath("/ddp/participants/FAKE_PARTICIPANT1/consentpdf"))
                .respond(response().withStatusCode(200).withBody(bytes));

        mockDDP.when(
                request().withPath("/ddp/participants/FAKE_PARTICIPANT1/releasepdf"))
                .respond(response().withStatusCode(200).withBody(bytes));
        mockDDP.when(
                request().withPath("/ddp/participants/FAKE_PARTICIPANT2/consentpdf"))
                .respond(response().withStatusCode(200).withBody(bytes));

        mockDDP.when(
                request().withPath("/ddp/participants/FAKE_PARTICIPANT2/releasepdf"))
                .respond(response().withStatusCode(200).withBody(bytes));
        mockDDP.when(
                request().withPath("/ddp/participants/FAKE_PARTICIPANT3/consentpdf"))
                .respond(response().withStatusCode(200).withBody(bytes));

        mockDDP.when(
                request().withPath("/ddp/participants/FAKE_PARTICIPANT3/releasepdf"))
                .respond(response().withStatusCode(200).withBody(bytes));
        mockDDP.when(
                request().withPath("/ddp/participants/FAKE_PARTICIPANT4/consentpdf"))
                .respond(response().withStatusCode(200).withBody(bytes));

        mockDDP.when(
                request().withPath("/ddp/participants/FAKE_PARTICIPANT4/releasepdf"))
                .respond(response().withStatusCode(200).withBody(bytes));
        mockDDP.when(
                request().withPath("/ddp/participants/FAKE_PARTICIPANT5/consentpdf"))
                .respond(response().withStatusCode(200).withBody(bytes));

        mockDDP.when(
                request().withPath("/ddp/participants/FAKE_PARTICIPANT5/releasepdf"))
                .respond(response().withStatusCode(200).withBody(bytes));
        logger.info("Finished setting up mock server! Now quartz can start ...");
    }

    private void nowCheckDBForKitRequests() throws Exception {
        Assert.assertTrue(DBTestUtil.checkIfValueExists(DBTestUtil.CHECK_KIT_REQUEST, "FAKE_PARTICIPANT1"));
        Assert.assertTrue(DBTestUtil.checkIfValueExists(DBTestUtil.CHECK_KIT_REQUEST, "FAKE_PARTICIPANT2"));
        Assert.assertTrue(DBTestUtil.checkIfValueExists(DBTestUtil.CHECK_KIT_REQUEST, "FAKE_PARTICIPANT3"));
        Assert.assertTrue(DBTestUtil.checkIfValueExists(DBTestUtil.CHECK_KIT_REQUEST, "FAKE_PARTICIPANT4"));
        Assert.assertTrue(DBTestUtil.checkIfValueExists(DBTestUtil.CHECK_KIT_REQUEST, "FAKE_PARTICIPANT5"));
        RouteTestSample.checkFileInBucket("FAKE_PARTICIPANT1/readonly/FAKE_PARTICIPANT1_consent", TEST_DDP.toLowerCase());
        RouteTestSample.checkFileInBucket("FAKE_PARTICIPANT1/readonly/FAKE_PARTICIPANT1_release", TEST_DDP.toLowerCase());
        RouteTestSample.checkFileInBucket("FAKE_PARTICIPANT2/readonly/FAKE_PARTICIPANT2_consent", TEST_DDP.toLowerCase());
        RouteTestSample.checkFileInBucket("FAKE_PARTICIPANT2/readonly/FAKE_PARTICIPANT2_release", TEST_DDP.toLowerCase());
        RouteTestSample.checkFileInBucket("FAKE_PARTICIPANT3/readonly/FAKE_PARTICIPANT3_consent", TEST_DDP.toLowerCase());
        RouteTestSample.checkFileInBucket("FAKE_PARTICIPANT3/readonly/FAKE_PARTICIPANT3_release", TEST_DDP.toLowerCase());
        RouteTestSample.checkFileInBucket("FAKE_PARTICIPANT4/readonly/FAKE_PARTICIPANT4_consent", TEST_DDP.toLowerCase());
        RouteTestSample.checkFileInBucket("FAKE_PARTICIPANT4/readonly/FAKE_PARTICIPANT4_release", TEST_DDP.toLowerCase());
        RouteTestSample.checkFileInBucket("FAKE_PARTICIPANT5/readonly/FAKE_PARTICIPANT5_consent", TEST_DDP.toLowerCase());
        RouteTestSample.checkFileInBucket("FAKE_PARTICIPANT5/readonly/FAKE_PARTICIPANT5_release", TEST_DDP.toLowerCase());
        RouteTestSample.checkBucket(TEST_DDP.toLowerCase());
    }

    private void nowCheckDBForMR() {
        Assert.assertTrue(DBTestUtil.checkIfValueExists(DBTestUtil.CHECK_PARTICIPANT, FAKE_DDP_PARTICIPANT_ID));
        Assert.assertTrue(DBTestUtil.checkIfValueExists(DBTestUtil.CHECK_PARTICIPANT, FAKE_DDP_PARTICIPANT_ID + "2"));
    }
}
