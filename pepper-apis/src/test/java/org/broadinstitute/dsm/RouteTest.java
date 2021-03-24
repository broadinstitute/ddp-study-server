package org.broadinstitute.dsm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import lombok.NonNull;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.email.SendGridEvent;
import org.broadinstitute.ddp.email.SendGridEventData;
import org.broadinstitute.ddp.handlers.util.*;
import org.broadinstitute.ddp.security.SecurityHelper;
import org.broadinstitute.dsm.db.*;
import org.broadinstitute.dsm.model.*;
import org.broadinstitute.dsm.model.mbc.MBC;
import org.broadinstitute.dsm.model.mbc.MBCInstitution;
import org.broadinstitute.dsm.model.mbc.MBCParticipant;
import org.broadinstitute.dsm.model.mbc.MBCParticipantInstitution;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.*;
import org.broadinstitute.dsm.util.tools.util.DBUtil;
import org.jruby.embed.ScriptingContainer;
import org.junit.*;
import org.mockserver.matchers.MatchType;
import org.mockserver.model.JsonBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;
import static org.junit.Assert.assertEquals;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * Test class for dsm endpoints
 * all angio response is only mocked,
 * to test the angio response try class: IntegrationTest
 */
public class RouteTest extends TestHelper {

    private static final Logger logger = LoggerFactory.getLogger(RouteTest.class);

    public static final String OUTPUT_FOLDER = "src/test/resources/output/";

    private static Object receiver = null;
    private static ScriptingContainer container = null;

    private static DDPMedicalRecordDataRequest ddpMedicalRecordDataRequest;

    private static final String GOOD_MONITORING_JWT =
            "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJvcmcuYnJvYWRpbnN0aXR1dGUua2R1eCIsIm1vbml0b3IiOiJnb29nbGUiLCJleHAiOjB9._TBBXUMDJq_ByWg1FJSIChld5IqSrzpyVB-BDbHH1ZM";

    private static final String BAD_MONITORING_JWT =
            "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJvcmcuYnJvYWRpbnN0aXR1dGUua2R1eCIsIm1vbml0b3IiOiJmdWNrIiwiZXhwIjowfQ.28JyxrY7BVC6HRoixHvXKqEC0LV_rRbFCsK2cH_-aUU";

    private static List<String> switchedOffDDPS;
    private static String userId;

    @BeforeClass
    public static void first() throws Exception {
        setupDB();

        startDSMServer();
        startMockServer();
        cleanDB();

        setupMock();
        setupUtils();
        setupScriptingContainer();

        if (new File(OUTPUT_FOLDER).exists()) {
            FileUtils.cleanDirectory(new File(OUTPUT_FOLDER));
        }
        addTestParticipant();
        userId = DBTestUtil.getTester("THE UNIT TESTER 1");
    }

    @AfterClass
    public static void last() {
        stopMockServer();
        stopDSMServer();
        cleanDB();
        cleanupDB();
    }

    @After
    public void afterTest() {
        DBTestUtil.deleteAllFieldSettings(TEST_DDP);
    }

    private static void setupScriptingContainer() {
        receiver = null;
        container = null;
        try {
            container = new ScriptingContainer();
            //put path to encryptorGem gem here
            container.getLoadPaths().add(container.getClassLoader().getResource(DSMServer.ENCRYPTION_PATH).getPath());
            container.runScriptlet("require 'encryptor'");
            // add script
            receiver = container.runScriptlet(DSMServer.SCRIPT);
        }
        catch (Exception e) {
            Assert.fail("Couldn't setup ruby for MBC decryption");
        }

        ddpMedicalRecordDataRequest = new DDPMedicalRecordDataRequest(container, receiver);
    }

    //kits will get deleted even if test failed!
    private static void cleanDB() {
        DBTestUtil.deleteAllParticipantData("66666666");
        DBTestUtil.deleteAllParticipantData("70000000");
        DBTestUtil.deleteAllParticipantData("75000000");
        DBTestUtil.deleteAllParticipantData("80000000");
        DBTestUtil.deleteAllParticipantData("SURVEY_PARTICIPANT", true);
        DBTestUtil.deleteAllParticipantData("SURVEY_PARTICIPANT_STATUS", true);
        DBTestUtil.deleteAllParticipantData("EXIT_PARTICIPANT", true);
        DBTestUtil.deleteAllParticipantData("FAKE_DDP_PARTICIPANT_Pepper", true);
        DBTestUtil.deleteAllParticipantData("FAKE_DDP_PARTICIPANT_Pepper_New", true);
        DBTestUtil.deleteAllParticipantData("1668888666");
        DBTestUtil.deleteAllParticipantData("1668888888");
        DBTestUtil.deleteAllParticipantData("FAKE_DDP_PARTICIPANT_Pepper1", true);
        DBTestUtil.deleteAllParticipantData("FAKE_DDP_PARTICIPANT_Pepper2", true);
        DBTestUtil.deleteAllParticipantData("FAKE_DDP_PARTICIPANT_Pepper3", true);
        DBTestUtil.deleteAllParticipantData("NEW_TEST_PARTICIPANT", true);
        DBTestUtil.deleteAllKitData("FAKE.MIGRATED_PARTICIPANT_ID");
        //remove all stuff which was added to db again, in case a test failed and db was not cleaned properly

        DBTestUtil.deleteFromQuery("simone+1@broadinstitute.org", DELETE_ASSIGNEE_EMAILS_QUERY);
        DBTestUtil.deleteFromQuery("simone+2@broadinstitute.org", DELETE_ASSIGNEE_EMAILS_QUERY);

        // deleted added oncHistorySetting
        DBTestUtil.deleteFromQuery("new setting", "DELETE FROM onc_history_detail_settings where column_name = ?");

        //delete unsent emails
        DBTestUtil.removedUnsentEmails();

        // delete field settings
        DBTestUtil.deleteAllFieldSettings(TEST_DDP);
    }

    private static void setupMock() throws Exception {
        setupDDPParticipantRoutes();
        setupDDPFollowUpRoutes();
        setupDDPMRRoutes();
        setupDDPParticipantEventRoutes();
    }

    private static void setupDDPParticipantEventRoutes() {
        mockDDP.when(request().withPath("/ddp/participantevent/FAKE_DDP_PARTICIPANT_IDeventUtilTest2")).respond(
                response().withStatusCode(200));
    }

    private static void setupDDPMRRoutes() throws Exception {
        String messageParticipant = TestUtil.readFile("ddpResponses/ParticipantInstitutions.json");
        mockDDP.when(
                request().withPath("/ddp/participantinstitutions"))
                .respond(response().withStatusCode(200).withBody(messageParticipant));

        messageParticipant = TestUtil.readFile("ddpResponses/ParticipantsMedical.json");
        mockDDP.when(
                request().withPath("/ddp/participants/FAKE_DDP_PARTICIPANT_ID/medical"))
                .respond(response().withStatusCode(200).withBody(messageParticipant));
    }

    private static void setupDDPFollowUpRoutes() throws Exception {
        //response for test 'listOfSurveys'
        String messageFollowupSurveys = TestUtil.readFile("ddpResponses/Followupsurveys.json");
        mockDDP.when(
                request().withPath("/ddp/followupsurveys"))
                .respond(response().withStatusCode(200).withBody(messageFollowupSurveys));

        mockDDP.when(
                request().withMethod("POST").withPath("/ddp/followupsurvey/test-consent").withBody(
                        JsonBody.json("{\"participantId\": \"SURVEY_PARTICIPANT\"}",
                                MatchType.STRICT
                        )
                ))
                .respond(response().withStatusCode(200));

        messageFollowupSurveys = TestUtil.readFile("ddpResponses/FollowupsurveyStatus.json");
        mockDDP.when(
                request().withMethod("GET").withPath("/ddp/followupsurvey/test-consent"))
                .respond(response().withStatusCode(200).withBody(messageFollowupSurveys));
    }

    private static void setupDDPParticipantRoutes() throws Exception {
        String messageParticipant = TestUtil.readFile("ddpResponses/Participants.json");
        mockDDP.when(
                request().withPath("/ddp/participants"))
                .respond(response().withStatusCode(200).withBody(messageParticipant));

        messageParticipant = TestUtil.readFile("ddpResponses/ParticipantsWithId.json");
        mockDDP.when(
                request().withPath("/ddp/participants/abcdefg"))
                .respond(response().withStatusCode(200).withBody(messageParticipant));
        mockDDP.when(
                request().withPath("/ddp/participants/" + FAKE_DDP_PARTICIPANT_ID))
                .respond(response().withStatusCode(200).withBody(messageParticipant));
        mockDDP.when(
                request().withPath("/ddp/participants/NEW_TEST_PARTICIPANT1"))
                .respond(response().withStatusCode(200).withBody(messageParticipant));
    }

    public List<Assignee> assigneeEndpoint() throws Exception {
        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, "/ui/" + "assignees?realm=" + TEST_DDP, testUtil.buildAuthHeaders()).returnResponse();

        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        String message = DDPRequestUtil.getContentAsString(response);
        Type listType = new TypeToken<ArrayList<Assignee>>() {
        }.getType();
        List<Assignee> assignees = new Gson().fromJson(message, listType);

        Assert.assertTrue(!assignees.isEmpty());

        //test if added assignee was returned
        boolean foundAssignee = false;
        for (Assignee assignee : assignees) {
            if (assignee.getName().equals("THE UNIT TESTER 1")) {
                foundAssignee = true;
                Assert.assertEquals("THE UNIT TESTER 1", assignee.getName());
                Assert.assertEquals("simone+1@broadinstitute.org", assignee.getEmail());
                break;
            }
        }

        //if assignee was not found
        if (!foundAssignee) {
            Assert.fail("Assignee was not found");
        }
        return assignees;
    }

    @Test
    public void assignParticipantMR() throws Exception {
        assignParticipantMR(TEST_DDP, FAKE_DDP_PARTICIPANT_ID);
    }

    private void assignParticipantMR(@NonNull String realm, @NonNull String participantId) throws Exception {
        String assigneeId = DBTestUtil.getTester("THE UNIT TESTER 1");
        if (assigneeId == null) {
            Assert.fail("Failed to setup db for test");
        }

        String dsmParticipantId = DBTestUtil.getParticipantIdOfTestParticipant(participantId);

        String json = "[{\"participantId\": \"" + dsmParticipantId + "\", \"assigneeId\": \"" + assigneeId + "\", \"email\": \"simone+1@broadinstitute.org\", \"shortId\": 666}]";
        HttpResponse response = TestUtil.perform(Request.Patch(DSM_BASE_URL + "/ui/" + "assignParticipant?realm=" + realm + "&assignMR=true"), json, testUtil.buildAuthHeaders()).returnResponse();

        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        List strings = new ArrayList<>();
        strings.add(participantId);
        String assignee = DBTestUtil.getStringFromQuery(SELECT_PARTICIPANT_QUERY, strings, "assignee_id_mr");
        Assert.assertEquals(assigneeId, assignee);
    }

    @Test
    public void assignParticipantTissue() throws Exception {
        String assigneeId = DBTestUtil.getTester("THE UNIT TESTER 1");
        if (assigneeId == null) {
            Assert.fail("Failed to setup db for test");
        }

        String participantId = DBTestUtil.getParticipantIdOfTestParticipant();

        String json = "[{\"participantId\": \"" + participantId + "\", \"assigneeId\": \"" + assigneeId + "\", \"email\": \"simone+1@broadinstitute.org\", \"shortId\": 666}]";
        HttpResponse response = TestUtil.perform(Request.Patch(DSM_BASE_URL + "/ui/" + "assignParticipant?realm=" + TEST_DDP + "&assignTissue=true"), json, testUtil.buildAuthHeaders()).returnResponse();

        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        List strings = new ArrayList<>();
        strings.add(FAKE_DDP_PARTICIPANT_ID);
        String assignee = DBTestUtil.getStringFromQuery(SELECT_PARTICIPANT_QUERY, strings, "assignee_id_tissue");
        Assert.assertEquals(assigneeId, assignee);
    }

    @Test
    public void participantAssignedEmail() throws Exception {
        participantAssignedEmail(TEST_DDP, FAKE_DDP_PARTICIPANT_ID);
    }

    private void participantAssignedEmail(@NonNull String realm, @NonNull String participantId) throws Exception {
        assignParticipantMR(realm, participantId);
        String email = DBTestUtil.getQueryDetail(SELECT_ASSIGNEE_BY_ID_QUERY, DBTestUtil.getAssigneeIdOfTestParticipant(participantId), "email");

        List strings = new ArrayList<>();
        strings.add(email);
        strings.add("NA");
        String emailId = DBTestUtil.getStringFromQuery(SELECT_EMAILQUEUE_QUERY, strings, "EMAIL_ID");
        Assert.assertNotNull(emailId);

        String dsmParticipantId = DBTestUtil.getParticipantIdOfTestParticipant(participantId);
        strings = new ArrayList<>();
        strings.add(dsmParticipantId);
        strings.add("PARTICIPANT_REMINDER");
        String emailIdReminder = DBTestUtil.getStringFromQuery(SELECT_EMAILQUEUE_QUERY, strings, "EMAIL_ID");
        Assert.assertNotNull(emailIdReminder);

        //change something in mr
        String medicalRecordId = DBTestUtil.getQueryDetail(MedicalRecord.SQL_SELECT_MEDICAL_RECORD + " and inst.ddp_institution_id = \"FAKE_DDP_PHYSICIAN_ID\" and p.ddp_participant_id = \"" + participantId + "\"", realm, "medical_record_id");
        String json = "{\"id\":\"" + medicalRecordId + "\",\"user\":\"simone+1@broadinstitute.org\",\"nameValue\":{\"name\":\"m.faxSent\",\"value\":\"2017-02-01\"}}";
        HttpResponse response = TestUtil.perform(Request.Patch(DSM_BASE_URL + "/ui/patch"), json, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        NotificationUtil notification = new NotificationUtil(cfg);
        notification.removeObsoleteReminders();

        emailIdReminder = DBTestUtil.getStringFromQuery(SELECT_EMAILQUEUE_QUERY, strings, "EMAIL_ID");
        Assert.assertNull(emailIdReminder);
    }

    @Test
    public void changeAssigneeDeleteReminderEmail() throws Exception {
        participantAssignedEmail();
        String participantId = DBTestUtil.getParticipantIdOfTestParticipant();

        String emailTester1 = DBTestUtil.getQueryDetail(SELECT_ASSIGNEE_BY_ID_QUERY, DBTestUtil.getAssigneeIdOfTestParticipant(FAKE_DDP_PARTICIPANT_ID), "email");

        String assigneeId = DBTestUtil.getTester("THE UNIT TESTER 2");

        String json = "[{\"participantId\": \"" + participantId + "\", \"assigneeId\": \"" + assigneeId + "\", \"email\": \"simone+2@broadinstitute.org\", \"shortId\": 666}]";
        HttpResponse response = TestUtil.perform(Request.Patch(DSM_BASE_URL + "/ui/" + "assignParticipant?realm=" + TEST_DDP + "&assignMR=true"), json, testUtil.buildAuthHeaders()).returnResponse();

        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        String emailTester2 = DBTestUtil.getQueryDetail(SELECT_ASSIGNEE_BY_ID_QUERY, DBTestUtil.getAssigneeIdOfTestParticipant(FAKE_DDP_PARTICIPANT_ID), "email");

        //tester changed
        Assert.assertNotEquals(emailTester1, emailTester2);

        List strings = new ArrayList<>();
        strings.add(participantId);
        strings.add("PARTICIPANT_REMINDER");
        String emailIdReminder = DBTestUtil.getStringFromQuery(SELECT_EMAILQUEUE_QUERY, strings, "EMAIL_ID");
        Assert.assertNotNull(emailIdReminder);

        String emailData = DBTestUtil.getStringFromQuery(SELECT_EMAILQUEUE_QUERY, strings, "EMAIL_DATA");
        Assert.assertTrue(emailData.indexOf("simone+1@broadinstitute.org") == -1);
        Assert.assertTrue(emailData.indexOf("simone+2@broadinstitute.org") != -1);
    }

    @Test
    public void downloadPDFEndpointConsent() throws Exception {
        downloadPDFEndpoint("Consent.pdf", "consentpdf", TEST_DDP);
        RouteTestSample.checkFileInBucket("FAKE_DDP_PARTICIPANT_ID/readonly/FAKE_DDP_PARTICIPANT_ID_consent", TEST_DDP.toLowerCase());
        RouteTestSample.checkBucket(TEST_DDP.toLowerCase());
    }

    @Test
    public void downloadPDFEndpointRelease() throws Exception {
        downloadPDFEndpoint("Release.pdf", "releasepdf", TEST_DDP);
        RouteTestSample.checkFileInBucket("FAKE_DDP_PARTICIPANT_ID/readonly/FAKE_DDP_PARTICIPANT_ID_release", TEST_DDP.toLowerCase());
        RouteTestSample.checkBucket(TEST_DDP.toLowerCase());
    }

    @Test
    public void downloadPDFEndpointCover() throws Exception {
        downloadPDFEndpoint("Cover.pdf", "cover", TEST_DDP);
        RouteTestSample.checkFileInBucket("FAKE_DDP_PARTICIPANT_ID/readonly/FAKE_DDP_PARTICIPANT_ID_cover", TEST_DDP.toLowerCase());
        RouteTestSample.checkBucket(TEST_DDP.toLowerCase());
    }

    @Test
    public void downloadPDFEndpointCoverNoBucketSave() throws Exception {
        addTestParticipant(TEST_DDP_2, FAKE_DDP_PARTICIPANT_ID, "FAKE_DDP_PHYSICIAN_ID");

        downloadPDFEndpoint("Cover.pdf", "cover", TEST_DDP_2);
        Assert.assertFalse(RouteTestSample.bucketExists(TEST_DDP_2));
    }

    private void downloadPDFEndpoint(String inFile, String ddpPath, String ddp) throws Exception {
        File file = TestUtil.getResouresFile(inFile);
        byte[] bytes = Files.readAllBytes(Paths.get(file.getPath()));

        //setting up mock angio
        mockDDP.when(
                request().withPath("/ddp/participants/" + FAKE_DDP_PARTICIPANT_ID + "/" + ddpPath))
                .respond(response().withStatusCode(200).withBody(bytes));

        if ("cover".endsWith(ddpPath)) {
            String medicalRecordId = editMedicalRecord(ddp, "m.name", "firtầữả", "name");
            ddpPath = ddpPath + "/" + medicalRecordId;
        }

        String json = "{\"ddpParticipantId\": \"" + FAKE_DDP_PARTICIPANT_ID + "\", \"userId\": \"26\", \"exchange_cb\": true}";
        HttpResponse response = TestUtil.perform(Request.Post(DSM_BASE_URL + "/ui/" + "downloadPDF" + "/" + ddpPath + "?realm=" + ddp), json, testUtil.buildAuthHeaders()).returnResponse();

        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        InputStream is = response.getEntity().getContent();
        TestUtil.generatePDF(is, OUTPUT_FOLDER, "Test_" + inFile);
        File generatedPDF = new File(OUTPUT_FOLDER, "Test_" + inFile);
        Assert.assertTrue(generatedPDF.exists());
    }

    @Test
    public void editMedicalRecordRequestMR() throws Exception {
        editMedicalRecord(TEST_DDP, "m.faxSent", "2017-02-01", "fax_sent");
    }

    @Test
    public void editMedicalRecordReceiveMR() throws Exception {
        editMedicalRecord(TEST_DDP, "m.mrReceived", "2017-02-06", "mr_received");
    }

    @Test
    public void changeMRFollowUp() throws Exception {
        DBTestUtil.createTestData(TEST_DDP, "NEW_TEST_PARTICIPANT", "TEST_INSTITUTION");
        String mrId = editMedicalRecord(TEST_DDP, "NEW_TEST_PARTICIPANT", "TEST_INSTITUTION", "m.followUps", "[{\\\"fRequest1\\\":\\\"2019-04-24\\\", \\\"fReceived\\\":\\\"2019-04-28\\\"}]", "follow_ups");
        FollowUp[] followUp = new Gson().fromJson(DBTestUtil.getQueryDetail("SELECT * from ddp_medical_record where medical_record_id = ? ", mrId, "follow_ups"), FollowUp[].class);
        Assert.assertEquals(followUp.length, 1);
        Assert.assertEquals(followUp[0].getFRequest1(), "2019-04-24");
        Assert.assertEquals(followUp[0].getFReceived(), "2019-04-28");

        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, "/ui/ddpInformation/2017-03-01/2020-03-20?realm=" + TEST_DDP + "&userId=" + userId, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        String message = DDPRequestUtil.getContentAsString(response);
        DashboardInformation ddps = new Gson().fromJson(message, DashboardInformation.class);
        Assert.assertNotNull(ddps);
        Map<String, Integer> map = new HashMap<>();
        //3 pf the pt in db are known to the mock server
        map.put("all", 3);
        map.put("status.ENROLLED", 3);
        Assert.assertEquals(map, ddps.getDashboardValues());

        mrId = editMedicalRecord(TEST_DDP, "NEW_TEST_PARTICIPANT", "TEST_INSTITUTION", "m.followUpRequired", "1", "followup_required");
        String followupRequired = DBTestUtil.getQueryDetail("SELECT * from ddp_medical_record where medical_record_id = ? ", mrId, "followup_required");
        Assert.assertEquals(followupRequired, "1");

        String text = "this is a follow up text fr an mr!";
        mrId = editMedicalRecord(TEST_DDP, "NEW_TEST_PARTICIPANT", "TEST_INSTITUTION", "m.followUpRequiredText", text, "followup_required_text");
        String followupRequiredText = DBTestUtil.getQueryDetail("SELECT * from ddp_medical_record where medical_record_id = ? ", mrId, "followup_required_text");
        Assert.assertEquals(text, followupRequiredText);

    }

    public String editMedicalRecord(String instanceName, String valueName, String value, String columnName) throws Exception {
        return editMedicalRecord(instanceName, FAKE_DDP_PARTICIPANT_ID, "FAKE_DDP_PHYSICIAN_ID", valueName, value, columnName);
    }

    @Test
    public void editOncHistory() throws Exception {
        ArrayList strings = new ArrayList<>();
        strings.add(TEST_DDP);
        strings.add(FAKE_DDP_PARTICIPANT_ID);

        String participantId = DBTestUtil.getParticipantIdOfTestParticipant();

        String json = "{\"id\":\"" + participantId + "\",\"user\":\"simone+1@broadinstitute.org\",\"nameValue\":{\"name\":\"" + "o.createdOncHistory" + "\",\"value\":\"" + "2017-01-01" + "\"}}";
        HttpResponse response = TestUtil.perform(Request.Patch(DSM_BASE_URL + "/ui/patch"), json, testUtil.buildAuthHeaders()).returnResponse();

        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        String created = DBTestUtil.getQueryDetail(SELECT_DATA_ONCHISTORY_QUERY, participantId, "created");

        Assert.assertEquals("2017-01-01", created);
    }

    @Test
    public void participantEndpoint() throws Exception {
        ParticipantWrapper[] participants = getParticipants("/ui/applyFilter?parent=participantList&userId=26&realm=" + TEST_DDP);
        Assert.assertNotNull(participants);
        Assert.assertTrue(participants.length > 0);
    }

    public static ParticipantWrapper[] getParticipants(String sentRequest) throws Exception {
        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, sentRequest, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        Gson gson = new GsonBuilder().create();
        ParticipantWrapper[] participants = gson.fromJson(DDPRequestUtil.getContentAsString(response), ParticipantWrapper[].class);
        return participants;
    }

    @Test
    public void assignParticipantAndParticipantAssigneeEndpointShowNotDeleted() throws Exception {
        String assigneeId = DBTestUtil.getTester("THE UNIT TESTER 1");
        if (assigneeId == null) {
            Assert.fail("Failed to setup db for test");
        }

        String participantId = DBTestUtil.getParticipantIdOfTestParticipant();
        String jsonAssign = "[{\"participantId\": \"" + participantId + "\", \"assigneeId\": \"" + assigneeId + "\", \"email\": \"simone+1@broadinstitute.org\", \"shortId\": 666}]";
        HttpResponse responseAssign = TestUtil.perform(Request.Patch(DSM_BASE_URL + "/ui/" + "assignParticipant?realm=" + TEST_DDP + "&assignMr=true"), jsonAssign, testUtil.buildAuthHeaders()).returnResponse();

        Assert.assertEquals(200, responseAssign.getStatusLine().getStatusCode());

        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, "/ui/applyFilter?parent=participantList&userId=26&realm=" + TEST_DDP, testUtil.buildAuthHeaders()).returnResponse();

        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        Gson gson = new GsonBuilder().create();
        ParticipantWrapper[] participants = gson.fromJson(DDPRequestUtil.getContentAsString(response), ParticipantWrapper[].class);
        Assert.assertNotNull(participants);
        Assert.assertTrue(participants.length > 0);
    }

    @Test
    public void institutionEndpoint() throws Exception {
        String json = "{\"realm\": \"" + TEST_DDP + "\", \"ddpParticipantId\": \"" + TestHelper.FAKE_DDP_PARTICIPANT_ID + "\", \"userId\": \"26\"}";
        HttpResponse response = TestUtil.perform(Request.Post(DSM_BASE_URL + "/ui/" + "institutions"), json, testUtil.buildAuthHeaders()).returnResponse();

        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        Gson gson = new GsonBuilder().create();
        MedicalInfo medicalInfo = gson.fromJson(DDPRequestUtil.getContentAsString(response), MedicalInfo.class);
        Assert.assertNotNull(medicalInfo);
        Assert.assertNotNull(medicalInfo.getInstitutions());
        Assert.assertTrue(medicalInfo.getInstitutions().size() > 0);
    }

    @Test
    public void dashboardEndpoint() throws Exception {
        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, "/ui/ddpInformation/2017-03-01/2017-03-20?realm=" + TEST_DDP + "&userId=" + userId, testUtil.buildAuthHeaders()).returnResponse();

        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        String message = DDPRequestUtil.getContentAsString(response);
        DashboardInformation ddps = new Gson().fromJson(message, DashboardInformation.class);
        Assert.assertNotNull(ddps);
    }

    @Ignore
    @Test
    public void doParticipantMedicalRecordsAsserts() throws Exception {
        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, "/ui/rawData/" + TEST_DDP + "?userId=" + userId, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        String message = DDPRequestUtil.getContentAsString(response);
        Type listType = new TypeToken<ArrayList<ParticipantWrapper>>() {
        }.getType();
        List<ParticipantWrapper> participants = new Gson().fromJson(message, listType);
        Assert.assertTrue(!participants.isEmpty());
    }

    @Test
    public void mailingListEndpointRealms() throws Exception {
        String assigneeId = DBTestUtil.getTester("THE UNIT TESTER 1");
        //set has_mailing_list of angio to 1
        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, "/ui/realmsAllowed?menu=mailingList&userId=" + assigneeId, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        Assert.assertNotNull(DDPRequestUtil.getContentAsString(response));
        Gson gson = new GsonBuilder().create();
        String[] realms = gson.fromJson(DDPRequestUtil.getContentAsString(response), String[].class);
        boolean foundAngio = false;
        for (String realm : realms) {
            if (TEST_DDP.equals(realm)) {
                foundAngio = true;
            }
        }
        Assert.assertTrue(foundAngio);
    }

    @Test
    public void testNdiInput() throws Exception {
        String headers = "participantId\tFirst\tMiddle\tLast\tYear\tMonth\tDay";
        String input = "";

        input += headers + "\n";

        String participantId1 = DirectMethodTest.randomStringGenerator(5, true, false, false);
        String firstNameShort = DirectMethodTest.randomStringGenerator(10, true, false, false);
        String lastNameShort = DirectMethodTest.randomStringGenerator(15, true, false, false);
        String middleLetter = DirectMethodTest.randomStringGenerator(1, true, false, false);
        String year1 = DirectMethodTest.randomStringGenerator(4, false, false, true);
        String month1 = DirectMethodTest.randomStringGenerator(2, false, false, true);
        String day1 = DirectMethodTest.randomStringGenerator(2, false, false, true);
        String line1 = participantId1 + "\t" + firstNameShort + "\t" + middleLetter + "\t" + lastNameShort + "\t" + year1 + "\t" + month1 + "\t" + day1;
        input += line1 + "\n";

        String participantId2 = DirectMethodTest.randomStringGenerator(5, true, false, false);
        String firstNameLong = DirectMethodTest.randomStringGenerator(20, true, false, false);
        String lastNameLong = DirectMethodTest.randomStringGenerator(25, true, false, false);
        String middleEmpty = DirectMethodTest.randomStringGenerator(0, true, false, false);
        String year2 = DirectMethodTest.randomStringGenerator(4, false, false, true);
        String month2 = DirectMethodTest.randomStringGenerator(1, false, false, true);
        String day2 = DirectMethodTest.randomStringGenerator(1, false, false, true);
        String line2 = participantId2 + "\t" + firstNameLong + "\t" + middleEmpty + "\t" + lastNameLong + "\t" + year2 + "\t" + month2 + "\t" + day2;
        input += line2;

        String userId = DBTestUtil.getTester("THE UNIT TESTER 1");
        HttpResponse response = TestUtil.perform(Request.Post(DSM_BASE_URL + "/ui/ndiRequest?userId=" + userId), input, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        String id1 = DBTestUtil.getQueryDetail("SELECT * FROM ddp_ndi WHERE ddp_participant_id = ?", participantId1, "ndi_id");
        String id2 = DBTestUtil.getQueryDetail("SELECT * FROM ddp_ndi WHERE ddp_participant_id = ?", participantId2, "ndi_id");
        DBTestUtil.deleteNdiAdded(id1);
        DBTestUtil.deleteNdiAdded(id2);
    }

    @Test
    public void testNdiUpload() throws Exception {
        String fileContent = TestUtil.readFile("NdiTestFile.txt");


        String userId = DBTestUtil.getTester("THE UNIT TESTER 1");
        HttpResponse response = TestUtil.perform(Request.Post(DSM_BASE_URL + "/ui/ndiRequest?userId=" + userId), fileContent, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        String content = new Gson().fromJson(DDPRequestUtil.getContentAsString(response), String.class);

        String ndiRow1 = content.substring(0, content.indexOf("\n"));
        Assert.assertEquals(100, ndiRow1.length());
        String last1 = ndiRow1.substring(0, 20);
        Assert.assertEquals("LLOOOOOOOOOOOOOOOOOO", last1);
        String first1 = ndiRow1.substring(20, 35);
        Assert.assertEquals("Pegah          ", first1);
        String middle1 = ndiRow1.substring(35, 36);
        Assert.assertEquals("M", middle1);
        Assert.assertEquals("         ", ndiRow1.substring(36, 45));
        String dob = ndiRow1.substring(45, 53);
        String mmDob = dob.substring(0, 2);
        Assert.assertEquals(Integer.parseInt(mmDob), Integer.parseInt("08"));
        String ddDob = dob.substring(2, 4);
        Assert.assertEquals(Integer.parseInt(ddDob), Integer.parseInt("06"));
        String yyyyDob = dob.substring(4);
        Assert.assertEquals(Integer.parseInt(yyyyDob), Integer.parseInt("1965"));
        String controlNumberNdi = ndiRow1.substring(81, 91);
        Assert.assertEquals("  ", ndiRow1.substring(98));
        char[] junk = new char[28];
        Arrays.fill(junk, ' ');
        String junks = String.valueOf(junk);
        Assert.assertEquals(junks, ndiRow1.substring(53, 81));
        Assert.assertEquals("         ", ndiRow1.substring(91));
        String ptIdInDB1 = DBTestUtil.getQueryDetail("SELECT * FROM ddp_ndi WHERE ndi_control_number = ? COLLATE utf8_bin", controlNumberNdi, "ddp_participant_id");
        Assert.assertEquals("1", ptIdInDB1);

        String ndiRow2 = content.substring(101, content.indexOf("\n", 102));
        Assert.assertEquals(100, ndiRow1.length());
        String last2 = ndiRow2.substring(0, 20);
        Assert.assertEquals("SHORTNAME           ", last2);
        String first2 = ndiRow2.substring(20, 35);
        Assert.assertEquals("FirstFirstFirst", first2);
        String middle2 = ndiRow2.substring(35, 36);
        Assert.assertEquals(" ", middle2);
        Assert.assertEquals("         ", ndiRow1.substring(36, 45));
        String dob2 = ndiRow2.substring(45, 53);
        String mmDob2 = dob2.substring(0, 2);
        Assert.assertEquals(Integer.parseInt(mmDob2), Integer.parseInt("10"));
        String ddDob2 = dob2.substring(2, 4);
        Assert.assertEquals(Integer.parseInt(ddDob2), Integer.parseInt("25"));
        String yyyyDob2 = dob2.substring(4);
        Assert.assertEquals(Integer.parseInt(yyyyDob2), Integer.parseInt("1985"));
        String controlNumberNdi2 = ndiRow2.substring(81, 91);
        Assert.assertEquals("  ", ndiRow2.substring(98));
        Assert.assertEquals(junks, ndiRow2.substring(53, 81));
        Assert.assertEquals("         ", ndiRow2.substring(91));
        String ptIdInDB2 = DBTestUtil.getQueryDetail("SELECT * FROM ddp_ndi WHERE ndi_control_number = ? COLLATE utf8_bin", controlNumberNdi2, "ddp_participant_id");
        Assert.assertEquals("2", ptIdInDB2);


        String id1 = DBTestUtil.getQueryDetail("SELECT * FROM ddp_ndi WHERE ddp_participant_id = ?", "1", "ndi_id");
        String id2 = DBTestUtil.getQueryDetail("SELECT * FROM ddp_ndi WHERE ddp_participant_id = ?", "2", "ndi_id");
        DBTestUtil.deleteNdiAdded(id1);
        DBTestUtil.deleteNdiAdded(id2);
    }

    @Test
    public void mailingListEndpointContacts() throws Exception {
        mockDDP.clear(request().withPath("/ddp/mailinglist"));
        String messageParticipant = TestUtil.readFile("ddpResponses/MailinglistNewContact.json");
        mockDDP.when(
                request().withPath("/ddp/mailinglist"))
                .respond(response().withStatusCode(200).withBody(messageParticipant));

        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, "/ui/mailingList/" + TEST_DDP, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        Assert.assertNotNull(DDPRequestUtil.getContentAsString(response));
        Gson gson = new GsonBuilder().create();
        Contact[] contacts = gson.fromJson(DDPRequestUtil.getContentAsString(response), Contact[].class);
        Assert.assertTrue(contacts.length > 0);
        for (Contact contact : contacts) {
            Assert.assertNotNull(contact.getFirstName());
            Assert.assertNotNull(contact.getLastName());
            Assert.assertNotNull(contact.getEmail());
            Assert.assertNotNull(contact.getDateCreated());
        }
    }

    @Test
    public void checkOldMailingListResponse() throws Exception {
        mockDDP.clear(request().withPath("/ddp/mailinglist"));
        String messageParticipant = TestUtil.readFile("ddpResponses/Mailinglist.json");
        mockDDP.when(
                request().withPath("/ddp/mailinglist"))
                .respond(response().withStatusCode(200).withBody(messageParticipant));

        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, "/ui/" + "mailingList/" + TEST_DDP, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        Assert.assertNotNull(DDPRequestUtil.getContentAsString(response));
        Gson gson = new GsonBuilder().create();
        Contact[] contacts = gson.fromJson(DDPRequestUtil.getContentAsString(response), Contact[].class);
        Assert.assertTrue(contacts.length > 0);
        for (Contact contact : contacts) {
            Assert.assertNotNull(contact.getFirstName());
            Assert.assertNotNull(contact.getLastName());
            Assert.assertNotNull(contact.getEmail());
            Assert.assertNull(contact.getDateCreated());
        }
    }

    @Test
    public void changeUserSettings() throws Exception {
        String userId = DBTestUtil.getTester("THE UNIT TESTER 1");
        HttpResponse response = TestUtil.perform(Request.Patch(DSM_BASE_URL + "/ui/" + "userSettings?userId=" + userId),
                "{\"rowsOnPage\": 10, \"rowSet0\": 5, \"rowSet1\": 10, \"rowSet2\": 15} ", testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        List strings = new ArrayList<>();
        strings.add("THE UNIT TESTER 1");
        Assert.assertEquals("10", DBTestUtil.getStringFromQuery(SELECT_USER_SETTING, strings, "rows_on_page"));
        Assert.assertEquals("5", DBTestUtil.getStringFromQuery(SELECT_USER_SETTING, strings, "rows_set_0"));
        Assert.assertEquals("10", DBTestUtil.getStringFromQuery(SELECT_USER_SETTING, strings, "rows_set_1"));
        Assert.assertEquals("15", DBTestUtil.getStringFromQuery(SELECT_USER_SETTING, strings, "rows_set_2"));

        //change value again, just to check if it really was changed
        response = TestUtil.perform(Request.Patch(DSM_BASE_URL + "/ui/" + "userSettings?userId=" + userId),
                "{\"rowsOnPage\": 30, \"rowSet0\": 10, \"rowSet1\": 20, \"rowSet2\": 30}", testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        Assert.assertEquals("30", DBTestUtil.getStringFromQuery(SELECT_USER_SETTING, strings, "rows_on_page"));
        Assert.assertEquals("10", DBTestUtil.getStringFromQuery(SELECT_USER_SETTING, strings, "rows_set_0"));
        Assert.assertEquals("20", DBTestUtil.getStringFromQuery(SELECT_USER_SETTING, strings, "rows_set_1"));
        Assert.assertEquals("30", DBTestUtil.getStringFromQuery(SELECT_USER_SETTING, strings, "rows_set_2"));
    }

    @Test
    public void editParticipantRecord() throws Exception {
        ArrayList strings = new ArrayList<>();
        strings.add(TEST_DDP);
        strings.add(FAKE_DDP_PARTICIPANT_ID);
        String partRecordId = DBTestUtil.getStringFromQuery(Participant.SQL_SELECT_PARTICIPANT + " and p.ddp_participant_id = ?", strings, "participant_id");

        String participantId = DBTestUtil.getParticipantIdOfTestParticipant();

        String json = "{\"id\":\"" + participantId + "\",\"user\":\"simone+1@broadinstitute.org\",\"nameValue\":{\"name\":\"" + "r.paperCRSent" + "\",\"value\":\"" + "2017-01-01" + "\"}}";
        HttpResponse response = TestUtil.perform(Request.Patch(DSM_BASE_URL + "/ui/patch"), json, testUtil.buildAuthHeaders()).returnResponse();

        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        String created = DBTestUtil.getQueryDetail(SELECT_PARTICIPANTRECORD_QUERY, partRecordId, "cr_sent");

        Assert.assertEquals("2017-01-01", created);
    }

    @Test
    @Ignore ("EEL is switched off for now")
    public void eelData() throws Exception {
        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, "/ui/" + "emailEvent/angio", testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        Gson gson = new GsonBuilder().create();
        SendGridEventData[] eventData = gson.fromJson(DDPRequestUtil.getContentAsString(response), SendGridEventData[].class);
        Assert.assertTrue(eventData.length > 0);
        List<String> listFromDB = DBTestUtil.getStringList("select SGE_DATA from SENDGRID_EVENT WHERE SGE_SOURCE = \"angio\"", "SGE_DATA", DBConstants.EEL_DB_NAME);
        List<String> templates = new ArrayList<>();
        for (String sourceFromDB : listFromDB) {
            gson = new GsonBuilder().create();
            SendGridEventData eventDataFromDB = gson.fromJson(sourceFromDB.replace("smtp-id", "smtp_id"), SendGridEventData.class);
            if (SendGridEvent.PROD_ENV.equals(eventDataFromDB.getDdp_env_type())) {
                if (!templates.contains(eventDataFromDB.getDdp_email_template())) {
                    templates.add(eventDataFromDB.getDdp_email_template());
                }
            }
        }
        Assert.assertTrue(eventData.length == templates.size());
    }

    @Test
    public void oncHistoryDetails() throws Exception {
        //add oncHistoryDetail
        String participantId = DBTestUtil.getParticipantIdOfTestParticipant();
        addOncHistoryDetails(participantId);

        //check if a oncHistoryDetail was returned
        String json = "{\"realm\": \"" + TEST_DDP + "\", \"ddpParticipantId\": \"" + TestHelper.FAKE_DDP_PARTICIPANT_ID + "\", \"userId\": \"26\"}";
        HttpResponse response = TestUtil.perform(Request.Post(DSM_BASE_URL + "/ui/" + "institutions"), json, testUtil.buildAuthHeaders()).returnResponse();

        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        Gson gson = new GsonBuilder().create();
        MedicalInfo medicalInfo = gson.fromJson(DDPRequestUtil.getContentAsString(response), MedicalInfo.class);
        Assert.assertNotNull(medicalInfo);
    }

    @Test
    public void changeOncHistoryDetail() throws Exception {
        //add oncHistoryDetail
        String participantId = DBTestUtil.getParticipantIdOfTestParticipant("FAKE_DDP_PARTICIPANT_ID");
        String oncHistoryId = addOncHistoryDetails(participantId);
        changeOncHistoryValue(oncHistoryId, participantId, "oD.datePX", "2017-01-20", "date_px");
        changeOncHistoryValue(oncHistoryId, participantId, "oD.tFaxSent", "2018-01-20", "fax_sent");

        oncHistoryId = addOncHistoryDetails(participantId);
        changeOncHistoryValue(oncHistoryId, participantId, "oD.datePX", "2017-01-26", "date_px");
        changeOncHistoryValue(oncHistoryId, participantId, "oD.tFaxSent", "2017-12-20", "fax_sent"); // a tissue requested before the previous one
    }

    @Test
    public void testGetFieldSettings() throws Exception {
        //Make sure GET correctly returns no settings
        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, "/ui/fieldSettings/" + TEST_DDP,
                testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals("testGetFieldSettings: GET returned non-200 error code instead of " +
                "blank list of settings", 200, response.getStatusLine().getStatusCode());
        Assert.assertEquals("testGetFieldSettings: GET didn't return an empty list", "{}", DDPRequestUtil.getContentAsString(response));

        //Add some settings directly to the database
        List<Value> oncPossibleValues = new ArrayList<>();
        oncPossibleValues.add(new Value("oncVla1"));
        DBTestUtil.createAdditionalFieldForRealm("nameOfOnc", "displayOfOnc",
                "oD", "select", oncPossibleValues);
        DBTestUtil.createAdditionalFieldForRealm("secondNameOfOnc", "second display of onc",
                "oD", "boolean", null);
        DBTestUtil.createAdditionalFieldForRealm("nameOfTissue", "display of Tissue",
                "t", "text", null);

        //Make sure GET returns the settings we created
        response = TestUtil.performGet(DSM_BASE_URL, "/ui/fieldSettings/" + TEST_DDP,
                testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals("testGetFieldSettings: GET returned error code", 200, response.getStatusLine().getStatusCode());
        String settingsString = DDPRequestUtil.getContentAsString(response);
        Gson gson = new Gson();
        Type settingsType = new TypeToken<Map<String, Collection<FieldSettings>>>() {
        }.getType();
        Map<String, Collection<FieldSettings>> returnedSettings = gson.fromJson(settingsString, settingsType);
        Assert.assertEquals("testGetFieldSettings: Returned wrong number of onchistory settings", 2, returnedSettings.get("oD").size());
        Assert.assertEquals("testGetFieldSettings: Returned wrong number of tissue settings", 1, returnedSettings.get("t").size());

        //Only 1 tissue but fake iterating
        for (FieldSettings tissueSetRet : returnedSettings.get("t")) {
            DBTestUtil.checkSettingMatch(tissueSetRet, "t",
                    "display of Tissue", "nameOfTissue",
                    "text", null, false,
                    "field named nameOfTissue");
        }

        //Check the onc settings
        FieldSettings[] oncSets = returnedSettings.get("oD").toArray(new FieldSettings[2]);
        FieldSettings onc1, onc2;
        if (oncSets[0].getColumnName().equals("nameOfOnc")) {
            onc1 = oncSets[0];
            onc2 = oncSets[1];
        }
        else {
            onc1 = oncSets[1];
            onc2 = oncSets[0];
        }

        DBTestUtil.checkSettingMatch(onc1, "oD",
                "displayOfOnc", "nameOfOnc", "select",
                oncPossibleValues, false, "field named nameOfOnc");

        DBTestUtil.checkSettingMatch(onc2, "oD",
                "second display of onc", "secondNameOfOnc",
                "boolean", null, false,
                "field named secondNameOfOnc");
    }

    @Test
    public void testPatchFieldSettings() throws IOException {
        List<Value> possibleValuesList = new ArrayList<>();
        possibleValuesList.add(new Value("multiop1"));
        possibleValuesList.add(new Value("multiop2"));
        String json = "{\"t\":[{\"fieldSettingId\":\"\",\"columnName\":\"nameOfTissueCol\"," +
                "\"columnDisplay\":\"display of TissueCol\",\"fieldType\":\"t\"," +
                "\"displayType\":\"multiselect\",\"possibleValues\":[{\"value\":\"multiop1\"},{\"value\":\"multiop2\"}]" +
                "}]}";
        HttpResponse response = TestUtil.perform(Request.Patch(DSM_BASE_URL + "/ui/fieldSettings/" +
                TEST_DDP), json, testUtil.buildAuthHeaders()).returnResponse();

        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        ArrayList<String> strings = new ArrayList<>();
        strings.add(TEST_DDP);
        String stringFromQuery = DBTestUtil.getStringFromQuery("select count(*) from field_settings where " +
                "ddp_instance_id= (select ddp_instance_id from ddp_instance where instance_name = ?) and not" +
                " (deleted <=> 1)", strings, "count(*)");

        Assert.assertEquals("testPatchFieldSettings: wrong number of settings returned", 1,
                Integer.parseInt(stringFromQuery));
        String idQuery = "select * from field_settings where ddp_instance_id = (select ddp_instance_id from " +
                "ddp_instance where instance_name = ?) and not (deleted <=> 1) and field_type = ?";
        strings.add("t");
        String tissueId = DBTestUtil.getStringFromQuery(idQuery, strings, "field_settings_id");

        //Make sure the setting matches what we expect
        DBTestUtil.checkSettingMatch(tissueId, "t", "nameOfTissueCol",
                "display of TissueCol", "multiselect", possibleValuesList,
                false);

        //Update the display name of the setting with patch
        json = "{\"t\":[{\"fieldSettingId\":\"" + tissueId + "\",\"columnName\":\"nameOfTissueCol\"," +
                "\"columnDisplay\":\"NEW display of tissue column\",\"deleted\":false,\"fieldType\":\"t\"," +
                "\"displayType\":\"multiselect\",\"possibleValues\":[{\"value\":\"multiop1\"},{\"value\":\"multiop2\"}]" +
                "}]}";

        response = TestUtil.perform(Request.Patch(DSM_BASE_URL + "/ui/fieldSettings/" + TEST_DDP), json,
                testUtil.buildAuthHeaders()).returnResponse();

        //Make sure it worked
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        DBTestUtil.checkSettingMatch(tissueId, "t", "nameOfTissueCol",
                "NEW display of tissue column", "multiselect",
                possibleValuesList, false);

        //Add another setting of the same type
        json = "{\"te\":[{\"fieldSettingId\":\"\",\"columnName\":\"tissue2n\",\"columnDisplay\":\"tissue2d\"," +
                "\"fieldType\":\"t\",\"displayType\":\"boolean\",\"possibleValues\":null}]}";

        response = TestUtil.perform(Request.Patch(DSM_BASE_URL + "/ui/fieldSettings/" + TEST_DDP), json,
                testUtil.buildAuthHeaders()).returnResponse();

        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        //Make sure there are now two settings
        strings.remove("t");
        stringFromQuery = DBTestUtil.getStringFromQuery("select count(*) from field_settings where " +
                        "ddp_instance_id= (select ddp_instance_id from ddp_instance where instance_name = ?) and not " +
                        "(deleted <=> 1)",
                strings, "count(*)");
        Assert.assertEquals("testPatchFieldSettings: wrong number of settings returned", 2,
                Integer.parseInt(stringFromQuery));

        //Get the ID of the second setting and make sure it matches what we expect
        String idQuery2 = "select * from field_settings where ddp_instance_id = (select ddp_instance_id from " +
                "ddp_instance where instance_name = ?) and not (deleted <=> 1) and field_type = ? and column_name = ?";
        strings.add("t");
        strings.add("tissue2n");
        String tissue2Id = DBTestUtil.getStringFromQuery(idQuery2, strings, "field_settings_id");
        DBTestUtil.checkSettingMatch(tissue2Id, "t", "tissue2n",
                "tissue2d", "boolean", null, false);

        //Delete the setting with patch
        json = "{\"t\":[{\"fieldSettingId\":\"" + tissueId + "\",\"columnName\":\"nameOfTissueCol\"," +
                "\"columnDisplay\":\"NEW display of tissue column\",\"deleted\":true," +
                "\"fieldType\":\"t\",\"displayType\":\"boolean\",\"possibleValues\":null}]}";

        response = TestUtil.perform(Request.Patch(DSM_BASE_URL + "/ui/fieldSettings/" + TEST_DDP), json,
                testUtil.buildAuthHeaders()).returnResponse();

        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        //Make sure there is now only 1 non-deleted setting and that it matches what we expect
        strings.remove("tissue2n");
        strings.remove("t");
        stringFromQuery = DBTestUtil.getStringFromQuery("select count(*) from field_settings where " +
                "ddp_instance_id = (select ddp_instance_id from ddp_instance where instance_name = ?) and not " +
                "(deleted <=> 1)", strings, "count(*)");
        Assert.assertEquals("testPatchFieldSettings: wrong number of settings returned", 1,
                Integer.parseInt(stringFromQuery));
        DBTestUtil.checkSettingMatch(tissue2Id, "t", "tissue2n",
                "tissue2d", "boolean", null, false);
    }

    @Test
    public void exitParticipantList() throws Exception {
        mockDDP.when(
                request().withPath("/ddp/exitparticipantrequest/EXIT_PARTICIPANT"))
                .respond(response().withStatusCode(200));
        exitPat("EXIT_PARTICIPANT");
        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, "/ui/" + "exitParticipant/" + TEST_DDP, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        Gson gson = new GsonBuilder().create();
        ParticipantExit[] exitedParticipants = gson.fromJson(DDPRequestUtil.getContentAsString(response), ParticipantExit[].class);
        Assert.assertEquals(1, exitedParticipants.length);
    }

    public static void exitPat(@NonNull String particpantId) throws Exception {
        DBTestUtil.createTestData(TEST_DDP, particpantId, "TEST_INSTITUTION");
        String json = "{\"realm\": \"" + TEST_DDP + "\", \"participantId\": \"" + particpantId + "\", \"user\": \"1\", \"inDDP\":true}";
        HttpResponse response = TestUtil.perform(Request.Post(DSM_BASE_URL + "/ui/" + "exitParticipant"), json, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        ArrayList strings = new ArrayList<>();
        strings.add(TEST_DDP);
        strings.add(particpantId);
        Assert.assertEquals("1", DBTestUtil.getStringFromQuery("select count(*) from ddp_participant_exit where ddp_instance_id = (select ddp_instance_id from ddp_instance where instance_name = ?) and ddp_participant_id = ?", strings, "count(*)"));
    }

    @Test
    public void listOfSurveys() throws Exception {
        DBTestUtil.createTestData(TEST_DDP, "SURVEY_PARTICIPANT", "TEST_INSTITUTION");
        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, "/ui/" + "triggerSurvey/" + TEST_DDP, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        Gson gson = new GsonBuilder().create();
        SurveyInfo[] surveyInfos = gson.fromJson(DDPRequestUtil.getContentAsString(response), SurveyInfo[].class);
        Assert.assertEquals(2, surveyInfos.length);
    }

    @Test
    public void triggerSingleSurvey() throws Exception {
        String assigneeId = DBTestUtil.getTester("THE UNIT TESTER 1");
        SimpleFollowUpSurvey simpleFollowUpSurvey = new SimpleFollowUpSurvey("SURVEY_PARTICIPANT");
        Gson gson = new Gson();
        String json = gson.toJson(simpleFollowUpSurvey, SimpleFollowUpSurvey.class);

        HttpResponse response = TestUtil.perform(Request.Post(DSM_BASE_URL + "/ui/" + "triggerSurvey?realm=" + TEST_DDP + "&userId=" + assigneeId + "&surveyName=test-consent&surveyType=REPEATING&isFileUpload=false"), json, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        Gson gson2 = new GsonBuilder().create();
        String message = DDPRequestUtil.getContentAsString(response);
        Result result = gson2.fromJson(message, Result.class);
        Assert.assertEquals(200, result.getCode());
    }

    @Test
    public void ddpNoSurveyStatus() throws Exception {
        try {
            DBTestUtil.createTestData(TEST_DDP, "SURVEY_PARTICIPANT_STATUS", "TEST_INSTITUTION_STATUS");
        }
        catch (Exception e) {
            //don't care if it is already there... just want to make sure that it is
        }
        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, "/ui/" + "triggerSurvey/" + TEST_DDP + "?surveyName=test-consent", testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        Gson gson = new GsonBuilder().create();
        String message = DDPRequestUtil.getContentAsString(response);
        Result result = gson.fromJson(message, Result.class);
        Assert.assertEquals(200, result.getCode());
        Assert.assertEquals("NO_SURVEY_STATUS", result.getBody());
    }

    @Test
    public void listOfSurveyStatus() {
        String roleId = DBTestUtil.getQueryDetail("SELECT * from instance_role where name = ?", "survey_status_endpoints", "instance_role_id");
        try {
            DBTestUtil.executeQuery("INSERT INTO ddp_instance_role SET ddp_instance_id = " + INSTANCE_ID + ", instance_role_id = " + roleId);
            DBTestUtil.createTestData(TEST_DDP, "SURVEY_PARTICIPANT_STATUS", "TEST_INSTITUTION_STATUS");
            HttpResponse response = TestUtil.performGet(DSM_BASE_URL, "/ui/" + "triggerSurvey/" + TEST_DDP + "?surveyName=test-consent", testUtil.buildAuthHeaders()).returnResponse();
            Assert.assertEquals(200, response.getStatusLine().getStatusCode());
            Gson gson = new GsonBuilder().create();
            String message = DDPRequestUtil.getContentAsString(response);
            logger.info(message);
            ParticipantSurveyInfo[] surveyStatusInfos = gson.fromJson(message, ParticipantSurveyInfo[].class);
            Assert.assertEquals(2, surveyStatusInfos.length);
        }
        catch (Exception e) {

        }
        finally {
            DBTestUtil.executeQuery("DELETE FROM ddp_instance_role WHERE ddp_instance_id = " + INSTANCE_ID + " and instance_role_id = " + roleId);
        }
    }

    @Test
    public void getMaxParticipantId() {
        inTransaction((conn) -> {
            try {
                long maxParticipantId = org.broadinstitute.dsm.util.DBUtil.getBookmark(conn, INSTANCE_ID);
                Assert.assertNotNull(maxParticipantId);
            }
            catch (Exception e) {
                throw new RuntimeException("value ", e);
            }
            return null;
        });
    }

    @Test
    public void getParticipantInstitutions() {
        inTransaction((conn) -> {
            try {
                long maxParticipantId = addParticipantIntoDb(conn);

                long newMaxParticipantId = org.broadinstitute.dsm.util.DBUtil.getBookmark(conn, INSTANCE_ID);
                Assert.assertNotNull(newMaxParticipantId);
                Assert.assertNotEquals(maxParticipantId, newMaxParticipantId);
                Assert.assertEquals(66666666, newMaxParticipantId);

                org.broadinstitute.dsm.util.DBUtil.updateBookmark(conn, maxParticipantId, INSTANCE_ID);

                DBTestUtil.deleteAllParticipantData(String.valueOf(newMaxParticipantId));
            }
            catch (Exception e) {
                throw new RuntimeException("getParticipantInstitutions ", e);
            }
            return null;
        });
    }

    public long addParticipantIntoDb(Connection conn) throws Exception {
        long maxParticipantId = org.broadinstitute.dsm.util.DBUtil.getBookmark(conn, INSTANCE_ID);
        mockDDP.clear(request().withPath("/ddp/institutionrequests/" + maxParticipantId));

        String messageParticipant = TestUtil.readFile("ddpResponses/Institutionrequests.json");
        mockDDP.when(
                request().withPath("/ddp/institutionrequests/" + maxParticipantId))
                .respond(response().withStatusCode(200)
                        .withBody(messageParticipant));

        ddpMedicalRecordDataRequest.requestAndWriteParticipantInstitutions();
        return maxParticipantId;
    }

    @Test
    public void medicalRecordLog() {
        inTransaction((conn) -> {
            try {
                //add participant with institutions
                String messageParticipant = TestUtil.readFile("ddpResponses/forQuartzJob/Institutionrequests_1.json");
                int instanceId = Integer.parseInt(INSTANCE_ID);
                long maxParticipantId = org.broadinstitute.dsm.util.DBUtil.getBookmark(conn, INSTANCE_ID);
                try {
                    addParticipant(messageParticipant, instanceId, maxParticipantId);
                }
                catch (Exception e) {
                    Assert.fail("Couldn't add test participant " + e);
                }

                //check if participant got added by checking if newMaxParticipantId changed
                long newMaxParticipantId = org.broadinstitute.dsm.util.DBUtil.getBookmark(conn, INSTANCE_ID);
                Assert.assertNotNull(newMaxParticipantId);
                Assert.assertNotEquals(maxParticipantId, newMaxParticipantId);

                //check if participant is added
                Assert.assertTrue(DBTestUtil.checkIfValueExists(DBTestUtil.CHECK_PARTICIPANT, FAKE_DDP_PARTICIPANT_ID));

                //add value for fax_sent
                List<String> strings = new ArrayList<>();
                strings.add(FAKE_DDP_PARTICIPANT_ID);
                strings = new ArrayList<>();
                strings.add("FAKE_DDP_PHYSICIAN_ID");

                String medicalRecordId = DBTestUtil.getQueryDetail(MedicalRecord.SQL_SELECT_MEDICAL_RECORD + " and inst.ddp_institution_id = \"FAKE_DDP_PHYSICIAN_ID\" and p.ddp_participant_id = \"" + FAKE_DDP_PARTICIPANT_ID + "\"", TEST_DDP, "medical_record_id");

                String json = "{\"id\":\"" + medicalRecordId + "\",\"user\":\"simone+1@broadinstitute.org\",\"nameValue\":{\"name\":\"m.faxSent\",\"value\":\"2017-02-01\"}}";
                HttpResponse response = TestUtil.perform(Request.Patch(DSM_BASE_URL + "/ui/patch"), json, testUtil.buildAuthHeaders()).returnResponse();

                Assert.assertEquals(200, response.getStatusLine().getStatusCode());
                String faxSent = DBTestUtil.getQueryDetail(RouteTest.SELECT_DATA_MEDICALRECORD_QUERY, medicalRecordId, "fax_sent");

                //check if faxSent is set into db
                Assert.assertEquals("2017-02-01", faxSent);

                //add new participant or institution information
                messageParticipant = TestUtil.readFile("ddpResponses/forQuartzJob/Institutionrequests_2.json");
                try {
                    addParticipant(messageParticipant, instanceId, maxParticipantId);
                }
                catch (Exception e) {
                    Assert.fail("Couldn't add test participant " + e);
                }

                //check if participant got added by checking if newMaxParticipantId is changed
                Assert.assertNotEquals(newMaxParticipantId, org.broadinstitute.dsm.util.DBUtil.getBookmark(conn, INSTANCE_ID).intValue());

                //check if a medicalRecord was generated
                String SQL_SELECT_MR_LOG = "SELECT medical_record_log_id, date, comments, type FROM ddp_medical_record_log WHERE medical_record_id = ?";
                String medicalRecordLogId = DBTestUtil.getQueryDetail(SQL_SELECT_MR_LOG, medicalRecordId, "medical_record_log_id");
                Assert.assertNotEquals("-1", medicalRecordLogId);

                //add new participant or institution information
                messageParticipant = TestUtil.readFile("ddpResponses/forQuartzJob/Institutionrequests_3.json");
                try {
                    addParticipant(messageParticipant, instanceId, maxParticipantId);
                }
                catch (Exception e) {
                    Assert.fail("Couldn't add test participant " + e);
                }

                //check if participant got added by checking if newMaxParticipantId is changed
                Assert.assertNotEquals(newMaxParticipantId, org.broadinstitute.dsm.util.DBUtil.getBookmark(conn, INSTANCE_ID).intValue());

                //check if no new medicalRecord was generated
                String logCount = DBTestUtil.getQueryDetail("select count(*) as count from ddp_medical_record_log where medical_record_id = ?", medicalRecordId, "count");
                Assert.assertEquals("1", logCount);

                //reset maxParticipantId
                strings = new ArrayList<>();
                strings.add(String.valueOf(maxParticipantId));
                strings.add(INSTANCE_ID);
                DBTestUtil.executeQueryWStrings("update bookmark set value = ? where instance = ? ", strings);
            }
            catch (Exception e) {
                throw new RuntimeException("medicalRecordLog ", e);
            }
            return null;
        });
    }

    private void addParticipant(String json, int instanceId, long maxParticipantId) {
        InstitutionRequest[] institutionRequests = new Gson().fromJson(json, InstitutionRequest[].class);
        if (institutionRequests != null && institutionRequests.length > 0) {
            for (InstitutionRequest participantInstitution : institutionRequests) {
                try {
                    inTransaction((conn) -> {
                        try {
                            ddpMedicalRecordDataRequest.writeParticipantIntoDb(conn, String.valueOf(instanceId), participantInstitution);
                        }
                        catch (Exception e) {
                            throw new RuntimeException("medicalRecordLog ", e);
                        }
                        return null;
                    });
                    maxParticipantId = Math.max(maxParticipantId, participantInstitution.getId());
                }
                catch (Exception e) {
                    logger.error("Failed to insert participant for mr into db ", e);
                }
            }
        }
        final long max = maxParticipantId;
        inTransaction((conn) -> {
            try {
                org.broadinstitute.dsm.util.DBUtil.updateBookmark(conn, max, INSTANCE_ID);
            }
            catch (Exception e) {
                throw new RuntimeException("medicalRecordLog ", e);
            }
            return null;
        });
    }

    @Test
    @Ignore("No more mbc per db, it is now on pepper")
    public void newMBCParticipantsByBookmark() {
        //need to request first all of them, because jobs are deactivated... and list will not get populated then
        ddpMedicalRecordDataRequest.requestFromDB();

        //get ddp_institution_id of last physician
        String maxPhysicianMBC = DBTestUtil.getQueryDetail(DBTestUtil.SELECT_MAXPARTICIPANT,
                DBTestUtil.getQueryDetail(DBUtil.GET_REALM_QUERY, "mbc", "ddp_instance_id"), "value");
        Map<String, MBCInstitution> mbcInstitutions = DSMServer.getMbcInstitutions();
        MBCInstitution institution = mbcInstitutions.get(maxPhysicianMBC);
        String updateAtBeforeChange = institution.getUpdatedAt();

        //change value of the last physician (in mbc db) in the physician list
        institution.setUpdatedAt("just now");
        DSMServer.putMBCInstitution(maxPhysicianMBC, institution);
        //double checking that value is now changed in DSM list
        mbcInstitutions = DSMServer.getMbcInstitutions();
        institution = mbcInstitutions.get(maxPhysicianMBC);
        String updateAtAfterChange = institution.getUpdatedAt();
        Assert.assertNotEquals(updateAtBeforeChange, updateAtAfterChange);

        //changing bookmarked ddp_institution_id to second last one
        List strings = new ArrayList<>();
        String newMaxPhysicianMBC = String.valueOf((Integer.parseInt(maxPhysicianMBC)) - 1);
        Assert.assertNotEquals(newMaxPhysicianMBC, maxPhysicianMBC);
        strings.add(newMaxPhysicianMBC);
        strings.add(DBTestUtil.getQueryDetail(DBUtil.GET_REALM_QUERY, "mbc", "ddp_instance_id"));
        DBTestUtil.executeQueryWStrings("update bookmark set value = ? where instance = ? ", strings);

        ddpMedicalRecordDataRequest.requestFromDB();
        //check value now after request
        mbcInstitutions = DSMServer.getMbcInstitutions();
        institution = mbcInstitutions.get(maxPhysicianMBC);
        String updateAtAfterRequest = institution.getUpdatedAt();
        Assert.assertNotEquals(updateAtAfterChange, updateAtAfterRequest);
        Assert.assertEquals(updateAtBeforeChange, updateAtAfterRequest);
    }

    @Test
    @Ignore("No more mbc per db, it is now on pepper")
    public void mbcParticipantsChanged() throws Exception {
        //need to request first all of them, because jobs are deactivated... and list will not get populated then
        ddpMedicalRecordDataRequest.requestFromDB();
        Map<String, MBCParticipant> mbcParticipants = DSMServer.getMbcParticipants();
        MBCParticipant participant = mbcParticipants.get("129");
        String originalUpdatedValue = participant.getUpdatedAt();
        String updatedChanged = "yesterday";
        MBCParticipant newParticipant = new MBCParticipant("129", "testName", "testNameLast", "wastelands", "01",
                "2016", "23-12-2015", "23-1-2000", updatedChanged);
        DSMServer.putMBCParticipant("129", newParticipant);

        String dbUrl = TransactionWrapper.getSqlFromConfig("mbc" + "." + MBC.URL);
        //get ddp_institution_id of last physician
        String maxPhysicianMBC = DBTestUtil.getQueryDetail(DBTestUtil.SELECT_MAXPARTICIPANT,
                DBTestUtil.getQueryDetail(DBUtil.GET_REALM_QUERY, "mbc", "ddp_instance_id"), "value");
        //new search does not change participant because nothing is updated
        ddpMedicalRecordDataRequest.requestFromDB();
        participant = mbcParticipants.get("129");
        //participant should still be the same as the one we set it to
        Assert.assertEquals(participant.getUpdatedAt(), updatedChanged);

        //setting update time back to before that participant was changed
        String strDtWithoutNanoSec = originalUpdatedValue.substring(0, originalUpdatedValue.lastIndexOf("."));
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date date = sdf.parse(strDtWithoutNanoSec);

        //using search method directly to be able to change the lastUpdate date
        MBCParticipantInstitution.getPhysiciansFromDB("mbc", dbUrl, Integer.parseInt(maxPhysicianMBC), date.getTime() - 36000000, container, receiver, false);
        participant = mbcParticipants.get("129");

        //participant value should be back to original ones
        Assert.assertNotEquals(participant.getUpdatedAt(), updatedChanged);
        Assert.assertEquals(participant.getUpdatedAt(), originalUpdatedValue);
    }

    @Test
    public void getDSMParticipantsMBC() {
        Map<String, Participant> participants = Participant.getParticipants("MBC");
        Assert.assertNotNull(participants);
        Assert.assertTrue(!participants.values().isEmpty());
    }

    @Test
    public void uptimeCheckPass() throws Exception {
        String appRoute = cfg.hasPath("portal.appRoute") ? cfg.getString("portal.appRoute") : null;
        Assert.assertTrue(StringUtils.isNotBlank(appRoute));

        Map<String, String> authHeaders = new HashMap<>();
        authHeaders.put("Authorization", "Bearer " + GOOD_MONITORING_JWT);

        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, appRoute + "upcheck", authHeaders).returnResponse();
        assertEquals(200, response.getStatusLine().getStatusCode());
    }

    @Test
    public void uptimeCheckFail() throws Exception {
        String appRoute = cfg.hasPath("portal.appRoute") ? cfg.getString("portal.appRoute") : null;
        Assert.assertTrue(StringUtils.isNotBlank(appRoute));

        Map<String, String> authHeaders = new HashMap<>();
        authHeaders.put("Authorization", "Bearer " + BAD_MONITORING_JWT);

        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, appRoute + "upcheck", authHeaders).returnResponse();
        assertEquals(401, response.getStatusLine().getStatusCode());
    }

    @Test
    public void uptimeCheckPassURL() throws Exception {
        Map<String, String> authHeaders = new HashMap<>();
        authHeaders.put("Authorization", "Bearer " + GOOD_MONITORING_JWT);

        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, "/app/upcheck", authHeaders).returnResponse();
        assertEquals(200, response.getStatusLine().getStatusCode());
    }

    @Test
    public void drugListEndpoint() throws Exception {
        String jwtDdpSecret = cfg.hasPath("portal.jwtDdpSecret") ? cfg.getString("portal.jwtDdpSecret") : null;
        Assert.assertTrue(StringUtils.isNotBlank(jwtDdpSecret));

        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, "/app/drugs", testUtil.buildHeaders(cfg.getString("portal.jwtDdpSecret"))).returnResponse();
        assertEquals(200, response.getStatusLine().getStatusCode());
    }

    @Test
    public void getDrugList() throws Exception {
        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, "/ui/drugList", testUtil.buildAuthHeaders()).returnResponse();
        assertEquals(200, response.getStatusLine().getStatusCode());
    }

    @Test
    public void addNewDrug() throws Exception {
        String oldDrugId = DBTestUtil.getStringFromQuery("select drug_id from drug_list where display_name = \'DRUG (TEST)\'", null, "drug_id");
        DBTestUtil.executeQuery("DELETE FROM drug_list WHERE drug_id = " + oldDrugId);

        String json = "{\"displayName\": \"DRUG (TEST)\", \"brandName\": \"DRUG\", \"chemocat\": \"TEST\", \"chemoType\": \"R\", \"studyDrug\": false, \"treatmentType\": \"H\", \"chemotherapy\": \"N\", \"active\": true}";

        HttpResponse response = TestUtil.perform(Request.Patch(DSM_BASE_URL + "/ui/drugList"), json, testUtil.buildAuthHeaders()).returnResponse();
        assertEquals(200, response.getStatusLine().getStatusCode());

        String drugId = DBTestUtil.getStringFromQuery("select drug_id from drug_list where display_name = \'DRUG (TEST)\'", null, "drug_id");
        Assert.assertNotNull(drugId);

        DBTestUtil.executeQuery("DELETE FROM drug_list WHERE drug_id = " + drugId);
    }

    @Test
    public void changeDrug() throws Exception {
        String drugId = DBTestUtil.getStringFromQuery("select drug_id from drug_list where display_name = \'ABARELIX (PLENAXIS)\'", null, "drug_id");

        //change value
        String json = "{\"id\":\"" + drugId + "\",\"user\":\"simone+1@broadinstitute.org\",\"nameValue\":{\"name\":\"" + "d.treatmentType" + "\",\"value\":\"" + "R" + "\"}}";
        HttpResponse response = TestUtil.perform(Request.Patch(DSM_BASE_URL + "/ui/patch"), json, testUtil.buildAuthHeaders()).returnResponse();

        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        String treatmentType = DBTestUtil.getStringFromQuery("select treatment_type from drug_list where display_name = \'ABARELIX (PLENAXIS)\'", null, "treatment_type");

        Assert.assertEquals("R", treatmentType);

        //change value back
        json = "{\"id\":\"" + drugId + "\",\"user\":\"simone+1@broadinstitute.org\",\"nameValue\":{\"name\":\"" + "d.treatmentType" + "\",\"value\":\"" + "H" + "\"}}";
        response = TestUtil.perform(Request.Patch(DSM_BASE_URL + "/ui/patch"), json, testUtil.buildAuthHeaders()).returnResponse();

        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        treatmentType = DBTestUtil.getStringFromQuery("select treatment_type from drug_list where display_name = \'ABARELIX (PLENAXIS)\'", null, "treatment_type");

        Assert.assertEquals("H", treatmentType);
    }

    @Test
    public void cancerListEndpoint() throws Exception {
        String jwtDdpSecret = cfg.hasPath("portal.jwtDdpSecret") ? cfg.getString("portal.jwtDdpSecret") : null;
        Assert.assertTrue(StringUtils.isNotBlank(jwtDdpSecret));

        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, "/app/cancers", testUtil.buildHeaders(cfg.getString("portal.jwtDdpSecret"))).returnResponse();
        assertEquals(200, response.getStatusLine().getStatusCode());
    }

    @Test
    public void discardImageUpload() throws Exception {
        String nameInBucket = "unitTest_fileUpload";
        File file = TestUtil.getResouresFile("BSPscreenshot.png");
        byte[] bytes = Files.readAllBytes(Paths.get(file.getPath()));

        HttpResponse response = TestUtil.perform(Request.Post(DSM_BASE_URL + "/ui/" + "discardUpload?userId=" + userId + "&kitDiscardId=1&realm=" + TEST_DDP + "&pathBSPScreenshot=" + nameInBucket), bytes, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        String json = "{\"path\": \"1_" + nameInBucket + "\"}";
        response = TestUtil.perform(Request.Patch(DSM_BASE_URL + "/ui/" + "showUpload?realm=" + TEST_DDP), json, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        response = TestUtil.perform(Request.Post(DSM_BASE_URL + "/ui/" + "discardUpload?kitDiscardId=1&userId=" + userId + "&realm=" + TEST_DDP + "&delete=true&pathBSPScreenshot=1_" + nameInBucket), bytes, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
    }

    @Test
    public void patchEndpoint500() throws Exception {
        HttpResponse response = TestUtil.perform(Request.Patch(DSM_BASE_URL + "/ui/patch"), "{}", testUtil.buildAuthHeaders()).returnResponse();
        assertEquals(500, response.getStatusLine().getStatusCode());
    }

    @Ignore ("Little method to test call to DDP Prostate")
    @Test
    public void download() throws Exception {
        String participantId = "6";

        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, "/ui/downloadPDF/" + participantId + "/releasepdf", testUtil.buildAuthHeaders()).returnResponse();

        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
    }

    @Test
    public void pepperSameEpochAgainAndAgain() throws Exception {
        String messageParticipant = TestUtil.readFile("ddpResponses/InstitutionrequestsPepper.json");
        mockDDP.when(
                request().withPath("/ddp/institutionrequests/1668888666"))
                .respond(response().withStatusCode(200)
                        .withBody(messageParticipant));

        inTransaction((conn) -> {
            try {
                //returning 3 times same response
                long currentMaxParticipantId = org.broadinstitute.dsm.util.DBUtil.getBookmark(conn, INSTANCE_ID);
                mockDDP.when(
                        request().withPath("/ddp/institutionrequests/" + currentMaxParticipantId))
                        .respond(response().withStatusCode(200)
                                .withBody(messageParticipant));
                ddpMedicalRecordDataRequest.requestAndWriteParticipantInstitutions();

                long newMaxParticipantId = org.broadinstitute.dsm.util.DBUtil.getBookmark(conn, INSTANCE_ID);
                Assert.assertNotNull(newMaxParticipantId);
                Assert.assertNotEquals(currentMaxParticipantId, newMaxParticipantId);
                Assert.assertEquals(1668888666, newMaxParticipantId);

                ddpMedicalRecordDataRequest.requestAndWriteParticipantInstitutions();
                ddpMedicalRecordDataRequest.requestAndWriteParticipantInstitutions();
                // now check what last pk in ddp_institution is
                String pkAfterMultipleCall = DBTestUtil.getStringFromQuery("SELECT * FROM ddp_institution order by institution_id desc limit 1", null, "institution_id");

                String messageParticipantNew = TestUtil.readFile("ddpResponses/InstitutionrequestsPepperNew.json");
                mockDDP.clear(request().withPath("/ddp/institutionrequests/1668888666"));
                mockDDP.when(
                        request().withPath("/ddp/institutionrequests/1668888666"))
                        .respond(response().withStatusCode(200)
                                .withBody(messageParticipantNew));
                ddpMedicalRecordDataRequest.requestAndWriteParticipantInstitutions(); //adding new pt into db

                long newMaxParticipantIdAfter = org.broadinstitute.dsm.util.DBUtil.getBookmark(conn, INSTANCE_ID);
                Assert.assertEquals(1668888888, newMaxParticipantIdAfter);
                String pkAfterNewCall = DBTestUtil.getStringFromQuery("SELECT * FROM ddp_institution order by institution_id desc limit 1", null, "institution_id");
                int newPK = Integer.parseInt(pkAfterMultipleCall) + 1; //new pt has only one institution

                Assert.assertEquals(String.valueOf(newPK), pkAfterNewCall);

                Assert.assertEquals(0, currentMaxParticipantId);
                org.broadinstitute.dsm.util.DBUtil.updateBookmark(conn, currentMaxParticipantId, INSTANCE_ID); //set it back to the bookmark before testing
            }
            catch (Exception e) {
                throw new RuntimeException("getParticipantInstitutions ", e);
            }
            return null;
        });
    }

    @Test
    public void pepperSameEpochMultiplePT() throws Exception {
        String messageParticipant = TestUtil.readFile("ddpResponses/InstitutionrequestsPepperMulti.json");
        inTransaction((conn) -> {
            try {
                long currentMaxParticipantId = org.broadinstitute.dsm.util.DBUtil.getBookmark(conn, INSTANCE_ID);
                mockDDP.clear(request().withPath("/ddp/institutionrequests/" + currentMaxParticipantId));
                mockDDP.when(
                        request().withPath("/ddp/institutionrequests/" + currentMaxParticipantId))
                        .respond(response().withStatusCode(200)
                                .withBody(messageParticipant));
                ddpMedicalRecordDataRequest.requestAndWriteParticipantInstitutions();

                long newMaxParticipantId = org.broadinstitute.dsm.util.DBUtil.getBookmark(conn, INSTANCE_ID);
                Assert.assertNotNull(newMaxParticipantId);
                Assert.assertNotEquals(currentMaxParticipantId, newMaxParticipantId);
                Assert.assertEquals(1668888444, newMaxParticipantId);
                String count = DBTestUtil.getStringFromQuery("SELECT count(*) FROM ddp_participant where last_version = '1668888444'", null, "count(*)");
                Assert.assertEquals("3", String.valueOf(count));

                Assert.assertEquals(0, currentMaxParticipantId);
                org.broadinstitute.dsm.util.DBUtil.updateBookmark(conn, currentMaxParticipantId, INSTANCE_ID); //set it back to the bookmark before testing
            }
            catch (Exception e) {
                throw new RuntimeException("getParticipantInstitutions ", e);
            }
            return null;
        });
    }

    public List<String> drugList() throws Exception {
        String appRoute = cfg.hasPath("portal.appRoute") ? cfg.getString("portal.appRoute") : null;
        Assert.assertTrue(StringUtils.isNotBlank(appRoute));

        String ddpSecret = cfg.getString("portal.jwtDdpSecret");
        String ddpToken = new SecurityHelper().createToken(ddpSecret, JWTRouteFilterTest.getCurrentUnixUTCTime() + JWTRouteFilterTest.THIRTY_MIN_IN_SECONDS, new HashMap<>());

        Map<String, String> authHeaders = new HashMap<>();
        authHeaders.put("Authorization", "Bearer " + ddpToken);

        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, appRoute + "drugs", authHeaders).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        String message = DDPRequestUtil.getContentAsString(response);
        Type listType = new TypeToken<ArrayList<String>>() {
        }.getType();
        List<String> drugs = new Gson().fromJson(message, listType);
        Assert.assertTrue(!drugs.isEmpty());
        String count = DBTestUtil.getStringFromQuery("SELECT count(*) FROM drug_list", null, "count(*)");
        Assert.assertEquals(count, String.valueOf(drugs.size()));
        return drugs;
    }

    @Test
    public void cancerList() throws Exception {
        String appRoute = cfg.hasPath("portal.appRoute") ? cfg.getString("portal.appRoute") : null;
        Assert.assertTrue(StringUtils.isNotBlank(appRoute));

        String ddpSecret = cfg.getString("portal.jwtDdpSecret");
        String ddpToken = new SecurityHelper().createToken(ddpSecret, JWTRouteFilterTest.getCurrentUnixUTCTime() + JWTRouteFilterTest.THIRTY_MIN_IN_SECONDS, new HashMap<>());

        Map<String, String> authHeaders = new HashMap<>();
        authHeaders.put("Authorization", "Bearer " + ddpToken);

        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, appRoute + "cancers", authHeaders).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        String message = DDPRequestUtil.getContentAsString(response);
        Type listType = new TypeToken<ArrayList<String>>() {
        }.getType();
        List<String> cancers = new Gson().fromJson(message, listType);
        Assert.assertTrue(!cancers.isEmpty());
        String count = DBTestUtil.getStringFromQuery("SELECT count(*) FROM cancer_list", null, "count(*)");
        Assert.assertEquals(count, String.valueOf(cancers.size()));
    }

    @Test
    public void changeTissue() throws Exception {
        //add Tissue
        String participantId = DBTestUtil.getParticipantIdOfTestParticipant("FAKE_DDP_PARTICIPANT_ID");
        String oncHistoryId = addOncHistoryDetails(participantId);

        changeOncHistoryValue(oncHistoryId, participantId, "oD.tFaxSent", "2018-01-20", "fax_sent");

        String tissueId = addTissue(oncHistoryId);
        changeTissueValue(tissueId, oncHistoryId, "t.sentGp", "2019-01-01", "sent_gp");
    }

    @Test
    public void typeAHead() throws Exception {
        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, "/ui/" + "lookup?field=tCollab&value=FAKE.MIGRATED_PARTICIPANT_ID&realm=migratedDDP&shortId=P84JE9", testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        String message = DDPRequestUtil.getContentAsString(response);
        Gson gson = new GsonBuilder().create();
        LookupResponse[] lookupResponse1 = gson.fromJson(message, LookupResponse[].class);
        Assert.assertEquals(1, lookupResponse1.length);
        Assert.assertEquals("MigratedProject_P84JE9", lookupResponse1[0].getField1().getValue());


        //insert a kit for pt of migrated ddp (will be uploaded with legacy shortId)
        DBTestUtil.insertLatestKitRequest(cfg.getString("portal.insertKitRequest"), cfg.getString("portal.insertKit"), "M1", 1, INSTANCE_ID_MIGRATED,
                "adr_6c3ace20442b49bd8fae9a661e481c9e", "shp_f470591c3fb441a68dbb9b76ecf3bb3d", "FAKE.MIGRATED_PARTICIPANT_ID");
        //change bsp_collaborator_ids
        DBTestUtil.executeQuery("UPDATE ddp_kit_request set bsp_collaborator_participant_id = \"MigratedProject_0111\", bsp_collaborator_sample_id =\"MigratedProject_0111_SALIVA\" where ddp_participant_id = \"FAKE.MIGRATED_PARTICIPANT_ID\"");

        response = TestUtil.performGet(DSM_BASE_URL, "/ui/" + "lookup?field=tCollab&value=FAKE.MIGRATED_PARTICIPANT_ID&realm=migratedDDP&shortId=P84JE9", testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        message = DDPRequestUtil.getContentAsString(response);
        LookupResponse[] lookupResponse2 = gson.fromJson(message, LookupResponse[].class);
        Assert.assertEquals(1, lookupResponse2.length);
        Assert.assertEquals("MigratedProject_0111", lookupResponse2[0].getField1().getValue());
        Assert.assertNotEquals(lookupResponse1[0].getField1(), lookupResponse2[0].getField1().getValue());
    }

    @Test
    public void displaySettings() throws Exception {
        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, "/ui/" + "displaySettings/angio?userId=26&parent=participantList", testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        List<Assignee> assignees = assigneeEndpoint();
        List<String> drugs = drugList();

        response = TestUtil.performGet(DSM_BASE_URL, "/ui/fieldSettings/" + TEST_DDP, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals("testGetFieldSettings: GET returned error code", 200, response.getStatusLine().getStatusCode());
        String settingsString = DDPRequestUtil.getContentAsString(response);
        Gson gson = new Gson();
        Type settingsType = new TypeToken<Map<String, Collection<FieldSettings>>>() {
        }.getType();
        Map<String, Collection<FieldSettings>> fieldSettings = gson.fromJson(settingsString, settingsType);
        //TODO add filters and activityDefinition
    }

    @Test
    public void listsEndPoint() throws Exception {
        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, "/ui/" + "displaySettings/" + TEST_DDP + "?userId=" + userId + "&parent=participantList", testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
    }

    @Test
    public void tissueListEndpoint() throws Exception {
        DBTestUtil.createTestData(TEST_DDP, "NEW_TEST_PARTICIPANT1", "TEST_INSTITUTION");
        String participantId = DBTestUtil.getParticipantIdOfTestParticipant("NEW_TEST_PARTICIPANT1");
        String oncHistoryId = addOncHistoryDetails(participantId);
        String tissueId1 = addTissue(oncHistoryId);
        Map<String, String> map = testUtil.buildAuthHeaders();
        map.put("realm", TEST_DDP);
        map.put("parent", "tissueList");
        map.put("userId", "26");
        map.put("userMail", "simone+1@broadinstitute.org");
        map.put("defaultFilter", "0");
        HttpResponse response = TestUtil.perform(Request.Patch(DSM_BASE_URL + "/ui/filterList?realm=" + TEST_DDP + "&parent=tissueList&userID=26&userMail=simone+1@broadinstitute.org&defaultFilter=0"), null, map).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        List<TissueListWrapper> results = new Gson().fromJson(DDPRequestUtil.getContentAsString(response), List.class);
        Assert.assertNotNull(results);
        Assert.assertNotEquals(0, results.size());

        changeTissueValue(tissueId1, oncHistoryId, "t.smId", "1224", "sm_id");
        String json = "{\"filters\":[{\"parentName\":\"t\",\"filter1\":{\"name\":\"smId\",\"value\":\"1224\"},\"filter2\":{\"name\":null,\"value\":null},\"exactMatch\":true,\"selectedOptions\":null,\"type\":\"TEXT\",\"range\":false,\"empty\":false,\"notEmpty\":false,\"participantColumn\":{\"display\":\"SM ID for H&E\",\"name\":\"smId\",\"tableAlias\":\"t\"}}],\"parent\":\"tissueList\",\"quickFilterName\":\"\"}";
        response = TestUtil.perform(Request.Patch(DSM_BASE_URL + "/ui/filterList?realm=" + TEST_DDP + "&parent=tissueList&userID=26&userMail=simone+1@broadinstitute.org"), json, map).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        results = new Gson().fromJson(DDPRequestUtil.getContentAsString(response), List.class);
        Assert.assertNotNull(results);
        Assert.assertNotEquals(0, results.size());
        Assert.assertEquals(1, results.size());
    }

    // TODO add missing tests
    // 1) when reminder emails functionality is getting added again, tests are needed!
}
