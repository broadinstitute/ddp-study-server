package org.broadinstitute.dsm;

import com.auth0.client.auth.AuthAPI;
import com.auth0.json.auth.TokenHolder;
import com.auth0.net.AuthRequest;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import lombok.NonNull;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.broadinstitute.dsm.db.*;
import org.broadinstitute.dsm.model.DashboardInformation;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.util.DBTestUtil;
import org.broadinstitute.dsm.util.DDPRequestUtil;
import org.broadinstitute.dsm.util.KitUtil;
import org.broadinstitute.dsm.util.TestUtil;
import org.junit.*;

import java.io.IOException;
import java.util.*;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class RouteInfoTest extends TestHelper {

    private static final String SQL_SELECT_MR_STATUS = "SELECT UNIX_TIMESTAMP(str_to_date(min(med.fax_sent), '%Y-%m-%d')) as mrRequested, " +
            "UNIX_TIMESTAMP(str_to_date(min(med.mr_received), '%Y-%m-%d')) as mrReceived, " +
            "UNIX_TIMESTAMP(str_to_date(min(onc.fax_sent), '%Y-%m-%d')) as tissueRequested, " +
            "UNIX_TIMESTAMP(str_to_date(min(onc.tissue_received), '%Y-%m-%d')) as tissueReceived, " +
            "UNIX_TIMESTAMP(str_to_date(min(tis.sent_gp), '%Y-%m-%d')) as tissueSent " +
            "FROM ddp_medical_record med " +
            "LEFT JOIN ddp_institution inst on (med.institution_id = inst.institution_id) LEFT JOIN ddp_participant as part on (part.participant_id = inst.participant_id) " +
            "LEFT JOIN ddp_onc_history_detail onc on (med.medical_record_id = onc.medical_record_id) LEFT JOIN ddp_tissue tis on (tis.onc_history_detail_id = onc.onc_history_detail_id) " +
            "WHERE part.ddp_participant_id = ? GROUP BY ddp_participant_id";

    private static AuthAPI ddpAuthApi = null;
    private static String accessToken = null;

    @BeforeClass
    public static void first() throws Exception {
        setupDB();
        startDSMServer();
        startMockServer();
        setupUtils();

        setupMock();
        addTestMigratedParticipant();
        ddpAuthApi = new AuthAPI(cfg.getString(ApplicationConfigConstants.AUTH0_ACCOUNT), cfg.getString(ApplicationConfigConstants.AUTH0_CLIENT_KEY), cfg.getString(ApplicationConfigConstants.AUTH0_SECRET));
        AuthRequest request = ddpAuthApi.requestToken(cfg.getString(ApplicationConfigConstants.AUTH0_AUDIENCE));
        TokenHolder tokenHolder = request.execute();
        accessToken = tokenHolder.getAccessToken();
    }

    private static void setupMock() throws Exception {
        setupDDPMRRoutes();
    }

    private static void setupDDPMRRoutes() throws Exception {
        String messageParticipant = TestUtil.readFile("ddpResponses/ParticipantInstitutions.json");
        mockDDP.when(
                request().withPath("/dsm/studies/migratedDDP/ddp/participantinstitutions"))
                .respond(response().withStatusCode(200).withBody(messageParticipant));
    }

    @AfterClass
    public static void last() {
        stopMockServer();
        stopDSMServer();
        cleanDB();
        cleanupDB();
    }

    @Before
    public void removeAddedKitsBeforeNextTest() throws Exception {
        DBTestUtil.deleteAllKitData("1112321.22-698-965-659-666");
        DBTestUtil.deleteAllFieldSettings(TEST_DDP);
    }

    //kits will get deleted even if test failed!
    private static void cleanDB() {
        DBTestUtil.deleteAllParticipantData("FAKE_MIGRATED_PARTICIPANT_ID", true);
        DBTestUtil.deleteAllKitData("1112321.22-698-965-659-666");
        DBTestUtil.deleteAllFieldSettings(TEST_DDP);
    }

    @Test
    public void participantStatusStudyNotFound() throws Exception {
        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, "/info/" + "participantstatus/TESTSDY1/123", getHeaderAppRoute()).returnResponse();
        Assert.assertEquals(404, response.getStatusLine().getStatusCode());
    }

    @Test
    public void allKitsTestEmptyParticipantList() throws Exception {
        HttpResponse response = TestUtil.perform(Request.Post(DSM_BASE_URL + "/app/batchKitsStatus/TESTSTUDY1"), "", testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(500, response.getStatusLine().getStatusCode());
    }

    @Test
    public void allKitsTest() throws Exception {
        DBTestUtil.createTestData(TEST_DDP, "TEST_PARTICIPANT_1", "TEST_INSTITUTION");
        DBTestUtil.createTestData(TEST_DDP, "TEST_PARTICIPANT_2", "TEST_INSTITUTION");
        DBTestUtil.createTestData(TEST_DDP, "TEST_PARTICIPANT_3", "TEST_INSTITUTION");

        String json = "{" +
                "\"participantIds\":[\"-2104929193.692d24f5-c0eb-4155-865f-2b2fb9ba99fd\", \"-2104929193.692d24f5-c0eb-4155-865f-2b2fb9ba99fd\",\"-2104929193.692d24f5-c0eb-4155-865f-2b2fb9ba99fd\"]" +
                "}";

        HttpResponse response = TestUtil.perform(Request.Post(DSM_BASE_URL + "/app/batchKitsStatus/GEC"), json, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
    }

    @Test
    public void participantStatusParticipantNotFound() throws Exception {
        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, "/app/" + "participantstatus/TESTSTUDY1/123", getHeaderAppRoute()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        String message = DDPRequestUtil.getContentAsString(response);
        Gson gson = new GsonBuilder().create();
        ParticipantStatus participantStatus = gson.fromJson(message, ParticipantStatus.class);
        Assert.assertNotNull(participantStatus);
        Assert.assertNull(participantStatus.getMrReceived());
        Assert.assertNull(participantStatus.getMrRequested());
        Assert.assertNull(participantStatus.getTissueReceived());
        Assert.assertNull(participantStatus.getTissueRequested());
        Assert.assertNull(participantStatus.getTissueSent());
        Assert.assertNull(participantStatus.getSamples());
    }

    @Test
    public void editMedicalRecordReceiveMRWithStatus() throws Exception {
        editMedicalRecord(TEST_DDP_MIGRATED, "FAKE_MIGRATED_PARTICIPANT_ID", "FAKE_MIGRATED_PHYSICIAN_ID", "m.mrReceived", "2017-02-06", "mr_received");
        participantStatus(TEST_DDP_MIGRATED, "FAKE_MIGRATED_PARTICIPANT_ID", "mrReceived");
    }

    //Helper method to format a list of names and a list of values into the format that additional_values_json expects
    private String constructValueForAdditionalValues(@NonNull List<String> names, @NonNull List<String> values) {
        Assert.assertFalse("constructValueForAdditionalValues: names was empty", names.isEmpty());
        Assert.assertFalse("constructValueForAdditionalValues: values was empty", values.isEmpty());
        Assert.assertEquals("constructValueForAdditionalValues: names and values are different sizes", names.size(), values.size());
        StringBuilder val = new StringBuilder("{");
        for (int i = 0; i < names.size(); i++) {
            val.append("\"");
            val.append(names.get(i));
            val.append("\":\"");
            val.append(values.get(i));
            val.append("\",");
        }

        String toRet = val.toString();
        toRet = val.substring(0, toRet.length() - 1) + "}";
        return toRet;
    }

    //Helper method to construct the nameValue for additionalValues that Gson parser can read
    private String constructEscapedAdditionalValues(@NonNull List<String> names, @NonNull List<String> values) {
        String additionalValues = constructValueForAdditionalValues(names, values);
        String escapedPart1 = additionalValues.replaceAll("\\\"", "\\\\\"");
        return "\"nameValue\":{\"name\":\"oD.additionalValues\",\"value\":\"" + escapedPart1 + "\"}";
    }

    @Test
    public void oncHistoryAdditionalValuesFormatTest() throws IOException {
        String addName1 = "FieldName";
        String addVal1 = "ValName";
        List<String> names = new ArrayList<>();
        List<String> vals = new ArrayList<>();
        names.add(addName1);
        vals.add(addVal1);

        //Format the additional_values_json
        String newFormat1 = constructEscapedAdditionalValues(names, vals);
        String newSubFormat1 = constructValueForAdditionalValues(names, vals);
        String participantId = DBTestUtil.getParticipantIdOfTestParticipant("FAKE_MIGRATED_PARTICIPANT_ID");
        String oncJsonNew = "{\"id\":null,\"parentId\":\"" + participantId + "\",\"parent\":\"participantId\",\"user\":\"simone+1@broadinstitute.org\"," + newFormat1 + "}";

        //Add onc history detail to the database with a patch
        HttpResponse response = TestUtil.perform(Request.Patch(DSM_BASE_URL + "/ui/patch"), oncJsonNew, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        //Make sure the patch succeeded
        String message = DDPRequestUtil.getContentAsString(response);
        JsonObject jsonObject = new JsonParser().parse(message).getAsJsonObject();
        String body = jsonObject.get("body").getAsString();
        jsonObject = new JsonParser().parse(body).getAsJsonObject();
        String oncHistoryDetailId = jsonObject.get("oncHistoryDetailId").getAsString();

        //Make sure the database has what we expect
        List<String> strings = new ArrayList<>();
        strings.add(oncHistoryDetailId);
        strings.add(newSubFormat1);
        String returned = DBTestUtil.getStringFromQuery("select * from ddp_onc_history_detail where onc_history_detail_id = ? and additional_values_json = ? order by last_changed desc limit 1", strings, "additional_values_json");
        Assert.assertEquals("oncHistoryAdditionalValuesFormat: additional_values_json in database did not match what we expected", newSubFormat1, returned);
    }

    @Test
    public void changeOncHistoryDetail() throws Exception {
        //add oncHistoryDetail
        String participantId = DBTestUtil.getParticipantIdOfTestParticipant("FAKE_MIGRATED_PARTICIPANT_ID");
        String oncHistoryId = addOncHistoryDetails(participantId);
        changeOncHistoryValue(oncHistoryId, participantId, "oD.datePX", "2017-01-20", "date_px");
        changeOncHistoryValue(oncHistoryId, participantId, "oD.tFaxSent", "2018-01-20", "fax_sent");
        Long faxSent = participantStatus(TEST_DDP_MIGRATED, "FAKE_MIGRATED_PARTICIPANT_ID", "tFaxSent"); // will return min fax

        oncHistoryId = addOncHistoryDetails(participantId);
        changeOncHistoryValue(oncHistoryId, participantId, "oD.datePX", "2017-01-26", "date_px");
        changeOncHistoryValue(oncHistoryId, participantId, "oD.tFaxSent", "2017-12-20", "fax_sent"); // a tissue requested before the previous one
        Long faxSent2 = participantStatus(TEST_DDP_MIGRATED, "FAKE_MIGRATED_PARTICIPANT_ID", "tFaxSent"); // will return min fax, which is now the last added one

        Assert.assertNotEquals(faxSent, faxSent2); // check that fax sent were not the same
        Assert.assertTrue(faxSent2 < faxSent); //test that second fax is smaller than first fax
    }

    @Test
    public void changeTissue() throws Exception {
        //add Tissue
        String participantId = DBTestUtil.getParticipantIdOfTestParticipant("FAKE_MIGRATED_PARTICIPANT_ID");
        String oncHistoryId = addOncHistoryDetails(participantId);

        changeOncHistoryValue(oncHistoryId, participantId, "oD.tFaxSent", "2018-01-20", "fax_sent");
        Long faxSent = participantStatus(TEST_DDP_MIGRATED, "FAKE_MIGRATED_PARTICIPANT_ID", "tFaxSent"); // will return min fax
        Assert.assertNotNull(faxSent); // check that fax sent is not null

        String tissueId = addTissue(oncHistoryId);
        changeTissueValue(tissueId, oncHistoryId, "t.sentGp", "2019-01-01", "sent_gp");
        Long gp = participantStatus(TEST_DDP_MIGRATED, "FAKE_MIGRATED_PARTICIPANT_ID", "sentGp"); // will return gp sent
        Assert.assertNotNull(gp); // check that fax sent is not null
    }

    @Test
    public void checkReturnStatus() throws Exception {
        String participantId = DBTestUtil.getParticipantIdOfTestParticipant("FAKE_MIGRATED_PARTICIPANT_ID");
        String oncHistoryId = addOncHistoryDetails(participantId);
        String tissueId = addTissue(oncHistoryId);
        String testFirstSmId = "FirstId";
        String testSHLWorkNumber = "shl test something";
        String testReturnFedexId = "testtest";
        String testReturnDate = "2019-03-11";

        changeTissueValue(tissueId, oncHistoryId, "t.tissueReturnDate", testReturnDate, "return_date");
        changeTissueValue(tissueId, oncHistoryId, "t.returnFedexId", testReturnFedexId, "return_fedex_id");
        changeTissueValue(tissueId, oncHistoryId, "t.firstSmId", testFirstSmId, "first_sm_id");
        changeTissueValue(tissueId, oncHistoryId, "t.shlWorkNumber", testSHLWorkNumber, "shl_work_number");

        String returnDate = getTestTissueInfo("return_date", tissueId);
        Assert.assertEquals(returnDate, testReturnDate);
        Assert.assertNotNull(returnDate);

        String request = getTestOncHistoryInfo("request", oncHistoryId);
        Assert.assertNotNull(request);
        Assert.assertEquals(request, "returned");

        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, "/ui/ddpInformation/2017-03-01/2020-03-20?realm=" + TEST_DDP_MIGRATED + "&userId=26", testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        String message = DDPRequestUtil.getContentAsString(response);
        DashboardInformation ddps = new Gson().fromJson(message, DashboardInformation.class);

        Assert.assertNotNull(ddps);
        int returned = ddps.getDashboardValues().get("request.returned");
        Assert.assertEquals(returned, 1); //former getTissueReturned()

        //to test it changes back
        changeTissueValue(tissueId, oncHistoryId, "t.tissueReturnDate", "", "return_date");
        request = getTestOncHistoryInfo("request", oncHistoryId);
        Assert.assertNotNull(request);
        Assert.assertEquals(request, "sent");

        changeOncHistoryValue(oncHistoryId, participantId, "oD.tissueReceived", testReturnDate, "tissue_received");
        changeTissueValue(tissueId, oncHistoryId, "t.tissueReturnDate", testReturnDate, "return_date");
        request = getTestOncHistoryInfo("request", oncHistoryId);
        Assert.assertEquals(request, "returned");
        changeTissueValue(tissueId, oncHistoryId, "t.tissueReturnDate", "", "return_date");
        request = getTestOncHistoryInfo("request", oncHistoryId);
        Assert.assertNotNull(request);
        Assert.assertEquals(request, "received");
        changeOncHistoryValue(oncHistoryId, participantId, "oD.tissueReceived", "", "tissue_received");
        String returnFId = getTestTissueInfo("return_fedex_id", tissueId);
        Assert.assertNotNull(returnFId);
        Assert.assertEquals(returnFId, testReturnFedexId);

        String firstSmId = getTestTissueInfo("first_sm_id", tissueId);
        Assert.assertNotNull(firstSmId);
        Assert.assertEquals(firstSmId, testFirstSmId);

        String shlWork = getTestTissueInfo("shl_work_number", tissueId);
        Assert.assertNotNull(testSHLWorkNumber);
        Assert.assertEquals(shlWork, testSHLWorkNumber);
    }

    @Test
    public void testDashboardTissueStatus() throws Exception {
        String participantId = DBTestUtil.getParticipantIdOfTestParticipant("FAKE_MIGRATED_PARTICIPANT_ID");
        String oncHistoryId = addOncHistoryDetails(participantId);
        String testReceivedDate = "2019-03-11";

        changeOncHistoryValue(oncHistoryId, participantId, "oD.tissueReceived", testReceivedDate, "tissue_received");
        String request = getTestOncHistoryInfo("request", oncHistoryId);
        Assert.assertNotNull(request);
        Assert.assertEquals(request, "received");

        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, "/ui/ddpInformation/2017-03-01/2020-03-20?realm=" + TEST_DDP_MIGRATED + "&userId=26", testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        String message = DDPRequestUtil.getContentAsString(response);
        DashboardInformation ddps = new Gson().fromJson(message, DashboardInformation.class);

        Assert.assertNotNull(ddps);
        int received = ddps.getDashboardValues().get("request.received");
        Assert.assertEquals(received, 1); // former getReceivedTissue()
    }

    @Test
    public void tissueAdditionalFieldsTest() throws Exception {
        ArrayList<String> name = new ArrayList<>();
        name.add("column1");
        DBTestUtil.createAdditionalFieldForRealm("column1", "column1Dsiplay", "t",
                "number", null);

        String participantId = DBTestUtil.getParticipantIdOfTestParticipant("FAKE_MIGRATED_PARTICIPANT_ID");
        String oncHistoryId = addOncHistoryDetails(participantId);
        String tissueId = addTissue(oncHistoryId);
        String testValue = "21";

        String json =
                "{\"id\":\"" + tissueId + "\",\"parentId\":\"" + oncHistoryId + "\",\"parent\":\"oncHistoryDetailId\",\"user\":\"ptaheri@broadinstitute.org\",\"nameValue\":{\"name\":\"t.additionalValues\",\"value\":\"{\\\"" + name.get(0) + "\\\":\\\"" + testValue + "\\\"}\"}}";
        HttpResponse response = TestUtil.perform(Request.Patch(DSM_BASE_URL + "/ui/" + "patch"), json, testUtil.buildAuthHeaders()).returnResponse();

        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        String additionalValues = getTestTissueInfo("additional_tissue_value_json", tissueId);
        TypeToken jsonMap = new TypeToken<Map<String, String>>(){};
        Map<String, String> valuesMap = new Gson().fromJson(additionalValues, jsonMap.getType());
        Assert.assertEquals(1, valuesMap.size());
        Assert.assertTrue(valuesMap.containsKey(name.get(0)));
        Assert.assertEquals(valuesMap.get(name.get(0)), testValue);
    }

    @Test
    public void participantStatus() throws Exception {
        //insert a kit for pt of migrated ddp (will be uploaded with legacy shortId)
        DBTestUtil.insertLatestKitRequest(cfg.getString("portal.insertKitRequest"), cfg.getString("portal.insertKit"), "M1", 1, INSTANCE_ID_MIGRATED,
                "adr_6c3ace20442b49bd8fae9a661e481c9e", "shp_f470591c3fb441a68dbb9b76ecf3bb3d", "1112321.22-698-965-659-666", 0);
        //change bsp_collaborator_ids
        DBTestUtil.executeQuery("UPDATE ddp_kit_request set bsp_collaborator_participant_id = \"MigratedProject_0011\", bsp_collaborator_sample_id =\"MigratedProject_0011_SALIVA\" where ddp_participant_id = \"1112321.22-698-965-659-666\"");

        ParticipantStatus participantStatus1 = participantStatus(TEST_DDP_MIGRATED, "1112321.22-698-965-659-666");
        Assert.assertNull(participantStatus1.getSamples());
        String ddpLabel = DBTestUtil.getQueryDetail("select ddp_label from ddp_kit_request where ddp_participant_id = ?", "1112321.22-698-965-659-666", "ddp_label");
        Long sent = System.currentTimeMillis();

        DBTestUtil.setKitToSent("MIGRATED_KIT_SENT", ddpLabel, sent);
        ParticipantStatus participantStatus2 = participantStatus(TEST_DDP_MIGRATED, "1112321.22-698-965-659-666");
        Assert.assertNotEquals(participantStatus1, participantStatus2);
        participantStatus(participantStatus2, "SALIVA", sent / 1000, null, false, false);

        KitUtil.getKitStatus();
        ParticipantStatus participantStatus3 = participantStatus(TEST_DDP_MIGRATED, "1112321.22-698-965-659-666");
        Assert.assertNotEquals(participantStatus3, participantStatus2);

        participantStatus(participantStatus3, "SALIVA", sent / 1000, null, false, true);
    }

    @Test
    public void poBoxParticipantStatus() throws Exception {
        //insert a kit for pt of migrated ddp (will be uploaded with legacy shortId)
        DBTestUtil.insertLatestKitRequest(cfg.getString("portal.insertKitRequest"), cfg.getString("portal.insertKit"), "M1", 1, INSTANCE_ID_MIGRATED,
                "adr_6c3ace20442b49bd8fae9a661e481c9e", null, "1112321.22-698-965-659-666", 0);

        //create label an therefore collaborator id
        RouteTestSample.triggerLabelCreationAndWaitForLabel(TEST_DDP_MIGRATED, "SALIVA", 60);

        ParticipantStatus participantStatus1 = participantStatus(TEST_DDP_MIGRATED, "1112321.22-698-965-659-666");
        Assert.assertNull(participantStatus1.getSamples());
        //set kit to sent
        String ddpLabel = DBTestUtil.getQueryDetail("select ddp_label from ddp_kit_request where ddp_participant_id = ?", "1112321.22-698-965-659-666", "ddp_label");
        Long sent = System.currentTimeMillis();
        DBTestUtil.setKitToSent("MIGRATED_KIT_SENT", ddpLabel, sent);

        ParticipantStatus participantStatus2 = participantStatus(TEST_DDP_MIGRATED, "1112321.22-698-965-659-666");
        Assert.assertNotEquals(participantStatus1, participantStatus2);
        participantStatus(participantStatus2, "SALIVA", sent / 1000, null, true, false);

        KitUtil.getKitStatus();
        ParticipantStatus participantStatus3 = participantStatus(TEST_DDP_MIGRATED, "1112321.22-698-965-659-666");
        Assert.assertEquals(participantStatus3, participantStatus2);
    }

    public Long participantStatus(String realm, String ddpParticipantId, String valueName) throws Exception {
        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, "/info/" + "participantstatus/" + realm + "/" + ddpParticipantId, getHeaderAppRoute()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        String message = DDPRequestUtil.getContentAsString(response);
        Gson gson = new GsonBuilder().create();
        ParticipantStatus participantStatus = gson.fromJson(message, ParticipantStatus.class);
        List strings = new ArrayList<>();
        strings.add(ddpParticipantId);
        if (valueName.equals("faxSent")) {
            String count = DBTestUtil.getStringFromQuery(SQL_SELECT_MR_STATUS, strings, "mrRequested");
            Assert.assertEquals(Long.valueOf(count), participantStatus.getMrRequested());
            return Long.valueOf(count);
        }
        if (valueName.equals("mrReceived")) {
            String count = DBTestUtil.getStringFromQuery(SQL_SELECT_MR_STATUS, strings, "mrReceived");
            Assert.assertEquals(Long.valueOf(count), participantStatus.getMrReceived());
            return Long.valueOf(count);
        }
        if (valueName.equals("tFaxSent")) {
            String count = DBTestUtil.getStringFromQuery(SQL_SELECT_MR_STATUS, strings, "tissueRequested");
            Assert.assertEquals(Long.valueOf(count), participantStatus.getTissueRequested());
            return Long.valueOf(count);
        }
        if (valueName.equals("tissueReceived")) {
            String count = DBTestUtil.getStringFromQuery(SQL_SELECT_MR_STATUS, strings, "tissueReceived");
            Assert.assertEquals(Long.valueOf(count), participantStatus.getTissueReceived());
            return Long.valueOf(count);
        }
        if (valueName.equals("sentGp")) {
            String count = DBTestUtil.getStringFromQuery(SQL_SELECT_MR_STATUS, strings, "tissueSent");
            Assert.assertEquals(Long.valueOf(count), participantStatus.getTissueSent());
            return Long.valueOf(count);
        }
        return null;
    }

    public String getTestOncHistoryInfo(String columnToReturn, String ondHistoryId) {
        List strings = new ArrayList<>();
        strings.add(ondHistoryId);
        String value = DBTestUtil.getStringFromQuery("SELECT * FROM  ddp_onc_history_detail onc WHERE onc.onc_history_detail_id = ?;",
                strings, columnToReturn);
        return value;
    }

    public String getTestTissueInfo(String columnToReturn, String tissueId) {
        List strings = new ArrayList<>();
        strings.add(tissueId);
        String value = DBTestUtil.getStringFromQuery("SELECT * FROM  ddp_tissue WHERE tissue_id = ?;", strings, columnToReturn);
        return value;
    }

    private ParticipantStatus participantStatus(String realm, String ddpParticipantId) throws Exception {
        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, "/info/" + "participantstatus/" + realm + "/" + ddpParticipantId, getHeaderAppRoute()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        String message = DDPRequestUtil.getContentAsString(response);
        Gson gson = new GsonBuilder().create();
        ParticipantStatus participantStatus = gson.fromJson(message, ParticipantStatus.class);
        return participantStatus;
    }

    private void participantStatus(ParticipantStatus participantStatus, String kitType, Long sent, Long received, boolean noTracking, boolean deliveredDateSet) {
        List<KitStatus> kitStatuses = participantStatus.getSamples();
        boolean found = false;
        if (kitStatuses != null) {
            for (KitStatus sample : kitStatuses) {
                if (noTracking) {
                    if (sample.getTrackingId() != null && sent != null) {
                        Assert.fail();
                    }
                    if (sample.getCarrier() != null && sent != null) {
                        Assert.fail();
                    }
                }
                else {
                    if (deliveredDateSet) {
                        if (sample.getDelivered() == null) {
                            Assert.fail();
                        }
                    }
                    if (sample.getTrackingId() == null && sent != null) {
                        Assert.fail();
                    }
                    if (!sample.getCarrier().equals("FedEx") && sent != null) {
                        Assert.fail();
                    }
                }
                if (sample.getSent() == null && sample.getReceived() == null) {
                    Assert.fail();
                    break;
                }
                if (sent != null && received != null) {
                    if (sample.getKitType().equals(kitType) && sample.getSent() == sent && sample.getReceived() == received) {
                        found = true;
                    }
                }
                else if (sent != null) {
                    if (sample.getKitType().equals(kitType) && sample.getSent().equals(sent) && sample.getReceived() == null) {
                        found = true;
                    }
                }
            }
            if (!found) {
                Assert.fail();
            }
        }
    }

    public static Map<String, String> getHeaderAppRoute() {
        Map<String, String> authHeaders = new HashMap<>();
        authHeaders.put("Authorization", "Bearer " + accessToken);

        return authHeaders;
    }
}
