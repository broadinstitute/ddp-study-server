package org.broadinstitute.dsm;

import com.easypost.model.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.broadinstitute.ddp.util.GoogleBucket;
import org.broadinstitute.dsm.db.*;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.exception.RateNotAvailableException;
import org.broadinstitute.dsm.model.*;
import org.broadinstitute.dsm.model.KitType;
import org.broadinstitute.dsm.model.gp.KitInfo;
import org.broadinstitute.dsm.model.gp.bsp.BSPKitRegistration;
import org.broadinstitute.dsm.model.gp.bsp.BSPKitStatus;
import org.broadinstitute.dsm.model.ddp.DDPParticipant;
import org.broadinstitute.dsm.route.KitStatusChangeRoute;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.*;
import org.broadinstitute.dsm.util.tools.util.DBUtil;
import org.junit.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class RouteTestSample extends TestHelper {

    private static final Logger logger = LoggerFactory.getLogger(RouteTestSample.class);

    private static final String EVENT_UTIL_TEST = "eventUtilTest";

    @BeforeClass
    public static void first() throws Exception {
        setupDB();

        startDSMServer();
        startMockServer();
        cleanDB();

        setupMock();
        setupUtils();
    }

    @AfterClass
    public static void last() {
        stopMockServer();
        stopDSMServer();
        cleanDB();
        cleanupDB();
    }

    //kits will get deleted even if test failed!
    private static void cleanDB() {
        DBTestUtil.deleteAllKitData(FAKE_BSP_TEST);
        DBTestUtil.deleteAllKitData("CA");
        DBTestUtil.deleteAllKitData("POBOX");
        DBTestUtil.deleteAllKitData("PepperParticipant");
        DBTestUtil.deleteAllKitData("OtherPepperParticipant");
        DBTestUtil.deleteAllKitData("CheapLabelPT");
        DBTestUtil.deleteAllKitData("8899777882");
        DBTestUtil.deleteAllKitData("9889938837");
        DBTestUtil.deleteAllKitData("33837748499");
        DBTestUtil.deleteAllKitData("9384658393");
        DBTestUtil.deleteAllKitData("3384948");
        DBTestUtil.deleteAllKitData("33839457566");
        DBTestUtil.deleteAllKitData("1771332079.c31d1fb7-82fd-4a86-81d5-6d01b846a8f7");
        DBTestUtil.deleteAllKitData("-2084982510.fe0f352f-3474-45ac-b0a9-974e24092b06");
        DBTestUtil.deleteAllKitData("1_3");
        DBTestUtil.deleteAllKitData("7");
        DBTestUtil.deleteAllKitData(VERY_LONG_PARTICIPANT_ID);
        DBTestUtil.deleteAllKitData(FAKE_DDP_PARTICIPANT_ID);
        DBTestUtil.deleteAllKitData("MULTIPLE_ACCOUNT_TEST");
        DBTestUtil.deleteAllKitData("MULTIPLE_ACCOUNT_TEST_666");
        DBTestUtil.deleteAllKitData("MULTIPLE_ACCOUNT_TEST_667");
        DBTestUtil.deleteAllKitData("MULTIPLE_ACCOUNT_TEST_1666");
        DBTestUtil.deleteAllKitData("MULTIPLE_ACCOUNT_TEST_1667");
        DBTestUtil.deleteAllKitData(FAKE_DDP_PARTICIPANT_ID + "_kit-1");
        DBTestUtil.deleteAllKitData(FAKE_DDP_PARTICIPANT_ID + "_kit-2");
        DBTestUtil.deleteAllKitData(FAKE_DDP_PARTICIPANT_ID + "_test");
        DBTestUtil.deleteAllKitData(FAKE_DDP_PARTICIPANT_ID + "_test2");
        DBTestUtil.deleteAllKitData(FAKE_DDP_PARTICIPANT_ID + "_tt1");
        DBTestUtil.deleteAllKitData(FAKE_DDP_PARTICIPANT_ID + "_tt2");
        DBTestUtil.deleteAllKitData(FAKE_DDP_PARTICIPANT_ID + "_tt3");
        DBTestUtil.deleteAllKitData(FAKE_DDP_PARTICIPANT_ID + "_tt4");
        DBTestUtil.deleteAllKitData(FAKE_DDP_PARTICIPANT_ID + "_tt6");
        DBTestUtil.deleteAllKitData(FAKE_DDP_PARTICIPANT_ID + "_tt7");
        DBTestUtil.deleteAllKitData(FAKE_DDP_PARTICIPANT_ID + "_tt8");
        DBTestUtil.deleteAllKitData(FAKE_DDP_PARTICIPANT_ID + "_tt9");
        DBTestUtil.deleteAllKitData(FAKE_DDP_PARTICIPANT_ID + "_express");
        DBTestUtil.deleteAllKitData(FAKE_DDP_PARTICIPANT_ID + "_express2");
        DBTestUtil.deleteAllKitData(FAKE_DDP_PARTICIPANT_ID + "_deactivate");
        DBTestUtil.deleteAllKitData(FAKE_DDP_PARTICIPANT_ID + "_deactivate2");
        DBTestUtil.deleteAllKitData(FAKE_DDP_PARTICIPANT_ID + "_EXPRESS_1");
        DBTestUtil.deleteAllKitData(FAKE_DDP_PARTICIPANT_ID + "_EXPRESS_2");
        DBTestUtil.deleteAllKitData(FAKE_DDP_PARTICIPANT_ID + "_BLOOD_TEST_DDP");
        DBTestUtil.deleteAllKitData(FAKE_DDP_PARTICIPANT_ID + "_BLOOD_ANOTHER_DDP");
        DBTestUtil.deleteAllKitData(FAKE_DDP_PARTICIPANT_ID + EVENT_UTIL_TEST);
        DBTestUtil.deleteAllKitData(FAKE_DDP_PARTICIPANT_ID + EVENT_UTIL_TEST + "2");
        DBTestUtil.deleteAllKitData(FAKE_DDP_PARTICIPANT_ID + "_exitKit");
        DBTestUtil.deleteAllKitData(FAKE_DDP_PARTICIPANT_ID + "_refundKit");
        DBTestUtil.deleteAllKitData(FAKE_DDP_PARTICIPANT_ID + "_BLOOD_TRACKING");
        DBTestUtil.deleteAllKitData(FAKE_DDP_PARTICIPANT_ID + "_BLOOD_SENT");
        DBTestUtil.deleteAllKitData(FAKE_DDP_PARTICIPANT_ID + "_SALIVA_SENT");
        DBTestUtil.deleteAllKitData(FAKE_DDP_PARTICIPANT_ID + "_DELIVERED_STATUS");
        DBTestUtil.deleteAllKitData("P123");
        DBTestUtil.deleteAllKitData("P124");
        DBTestUtil.deleteAllKitData("00004");
        DBTestUtil.deleteAllKitData("00003");
        DBTestUtil.deleteAllKitData(FAKE_DDP_PARTICIPANT_ID + "_SENT_REMINDER_TEST");
        DBTestUtil.deleteAllKitData("1112321.22-698-965-659-666");
        DBTestUtil.deleteAllKitData("1112321.22-698-965-659-667");
        DBTestUtil.deleteAllKitData("1112321.22-698-965-659-668");
        DBTestUtil.deleteAllKitData("DLF90348FK65DIR88");

        DBTestUtil.deleteAllParticipantData("1112321.22-698-965-659-668", true);
        DBTestUtil.deleteAllParticipantData(FAKE_DDP_PARTICIPANT_ID + "_exitKit", true);
        DBTestUtil.deleteAllParticipantData(FAKE_DDP_PARTICIPANT_ID + "_refundKit", true);

        //delete unsent emails
        DBTestUtil.removedUnsentEmails();
    }

    private static void setupMock() throws Exception {
        setupDDPKitRoutes();
        setupDDPParticipantRoutes();
    }

    private static void setupDDPKitRoutes() throws Exception {
        String message = TestUtil.readFile("ddpResponses/Kitrequests.json");
        mockDDP.when(
                request().withPath("/ddp/kitrequests"))
                .respond(response().withStatusCode(200).withBody(message));

        message = TestUtil.readFile("ddpResponses/KitrequestsWithId.json");
        mockDDP.when(
                request().withPath("/ddp/kitrequests/" + FAKE_LATEST_KIT))
                .respond(response().withStatusCode(200).withBody(message));
    }

    private static void setupDDPParticipantRoutes() throws Exception {
        String messageParticipant = TestUtil.readFile("ddpResponses/Participants.json");
        mockDDP.when(
                request().withPath("/ddp/participants"))
                .respond(response().withStatusCode(200).withBody(messageParticipant));

        messageParticipant = TestUtil.readFile("ddpResponses/ParticipantsWithIdWithoutShortId.json");
        mockDDP.when(
                request().withPath("/ddp/participants/" + VERY_LONG_PARTICIPANT_ID))
                .respond(response().withStatusCode(200).withBody(messageParticipant));

        messageParticipant = TestUtil.readFile("ddpResponses/ParticipantsWithIdCA.json");
        mockDDP.when(
                request().withPath("/ddp/participants/CA"))
                .respond(response().withStatusCode(200).withBody(messageParticipant));

        messageParticipant = TestUtil.readFile("ddpResponses/ParticipantsWithIdPOBox.json");
        mockDDP.when(
                request().withPath("/ddp/participants/POBOX"))
                .respond(response().withStatusCode(200).withBody(messageParticipant));

        messageParticipant = TestUtil.readFile("ddpResponses/ParticipantsWithId.json");
        mockDDP.when(
                request().withPath("/ddp/participants/" + FAKE_BSP_TEST))
                .respond(response().withStatusCode(200).withBody(messageParticipant));
        mockDDP.when(
                request().withPath("/ddp/participants/" + FAKE_DDP_PARTICIPANT_ID))
                .respond(response().withStatusCode(200).withBody(messageParticipant));
        mockDDP.when(
                request().withPath("/ddp/participants/CheapLabelPT"))
                .respond(response().withStatusCode(200).withBody(messageParticipant));

        messageParticipant = TestUtil.readFile("ddpResponses/ParticipantsWithIdWithNewField.json");
        mockDDP.when(
                request().withPath("/ddp/participants/PepperParticipant"))
                .respond(response().withStatusCode(200).withBody(messageParticipant));

        messageParticipant = TestUtil.readFile("ddpResponses/ParticipantsWithIdWithNewFieldAsNull.json");
        mockDDP.when(
                request().withPath("/ddp/participants/OtherPepperParticipant"))
                .respond(response().withStatusCode(200).withBody(messageParticipant));
    }

    @Test
    public void getKitLookup() {
        HashMap<String, org.broadinstitute.dsm.model.KitType> kitLookup = org.broadinstitute.dsm.model.KitType.getKitLookup();
        Assert.assertNotNull(kitLookup);

        String key = "SALIVA" + "_" + INSTANCE_ID;
        KitType kitType = kitLookup.get(key);
        Assert.assertNotNull(kitType);
    }

    @Test
    public void createRandom() {
        Assert.assertNotNull(KitRequestShipping.createRandom(20));
    }

    @Test
    public void generateDdpLabelID() {
        Assert.assertNotNull(KitRequestShipping.generateDdpLabelID());
    }

    @Test
    public void generateDdpLabelIDDoesStartWithNumber() {
        Assert.assertTrue(!KitRequestShipping.generateDdpLabelID().startsWith("\\d.*"));
    }

    @Test
    public void generateBspParticipantID() {
        String id = KitRequestShipping.generateBspParticipantID("TestProject", null, "23");
        Assert.assertNotNull(id);
        Assert.assertEquals("TestProject_0023", id);
    }

    @Test
    public void generateBspParticipantIDWithoutPrefix() {
        String id = KitRequestShipping.generateBspParticipantID(null, null, "23");
        Assert.assertNotNull(id);
        Assert.assertEquals("0023", id);
    }

    @Test
    public void generateBspParticipantIDWithoutPrefixAndShortId() {
        String participantId = "ihdfijaefiopq3r9e4kl";
        DDPParticipant participant = new DDPParticipant();
        participant.setParticipantId(participantId);
        String id = KitRequestShipping.generateBspParticipantID(null, null, participant.getShortId());
        Assert.assertNotNull(id);
        Assert.assertEquals(participantId, id);
    }

    @Test
    public void getAllKitRequests() {
        DBTestUtil.deleteAllKitData(FAKE_DDP_PARTICIPANT_ID);
        // Getting all KitRequests (because of the null --> getting whole list)
        List<LatestKitRequest> latestKitRequests = new ArrayList<>();
        latestKitRequests.add(new LatestKitRequest(null, INSTANCE_ID, TEST_DDP, DDP_BASE_URL,
                DBTestUtil.getQueryDetail(DBUtil.GET_REALM_QUERY, TEST_DDP, DBConstants.COLLABORATOR_ID_PREFIX),
                false, false, false, false, null));
        ddpKitRequest.requestAndWriteKitRequests(latestKitRequests);

        triggerLabelCreationAndWaitForLabel(TEST_DDP, null, 60);

        // Checking if responded DDPKitRequest is in db
        Assert.assertTrue(DBTestUtil.checkIfValueExists(DBTestUtil.CHECK_KIT_REQUEST, FAKE_BSP_TEST));
        Assert.assertTrue(DBTestUtil.checkIfValueExists(DBTestUtil.CHECK_KIT_REQUEST, "CA"));
        Assert.assertTrue(DBTestUtil.checkIfValueExists(DBTestUtil.CHECK_KIT_REQUEST, "POBOX"));
        Assert.assertTrue(DBTestUtil.checkIfValueExists(DBTestUtil.CHECK_KIT, VERY_LONG_PARTICIPANT_ID));
        checkError("CA");
        checkError("POBOX");

        //check collaborator sample id
        Assert.assertEquals("TestProject_0007_SALIVA_2", DBTestUtil.getQueryDetail("select * from ddp_kit_request where ddp_participant_id = ?", FAKE_BSP_TEST, "bsp_collaborator_sample_id"));
        Assert.assertEquals("TestProject_0007_SALIVA_3", DBTestUtil.getQueryDetail("select * from ddp_kit_request where ddp_participant_id = ?", "CA", "bsp_collaborator_sample_id"));
        Assert.assertEquals("TestProject_0007_SALIVA_4", DBTestUtil.getQueryDetail("select * from ddp_kit_request where ddp_participant_id = ?", "POBOX", "bsp_collaborator_sample_id"));
        Assert.assertNull(DBTestUtil.getQueryDetail("select * from ddp_kit_request where ddp_participant_id = ?", VERY_LONG_PARTICIPANT_ID, "bsp_collaborator_sample_id"));
    }

    @Test
    public void getLatestKitRequests() {
        DBTestUtil.deleteAllKitData(FAKE_DDP_PARTICIPANT_ID);
        DBTestUtil.insertLatestKitRequest(cfg.getString("portal.insertKitRequest"), cfg.getString("portal.insertKit"), "", 1,
                INSTANCE_ID);
        // Getting the latestKitRequest from DDP and writing into ddp_kit_request
        ddpKitRequest.requestAndWriteKitRequests(LatestKitRequest.getLatestKitRequests());

        // Checking if responded DDPKitRequest is in db
        Assert.assertTrue(DBTestUtil.checkIfValueExists(DBTestUtil.CHECK_KITREQUEST, "FAKE_NEWER_KIT"));
    }

    @Test
    public void generateBspSampleID() {
        inTransaction((conn) -> {
            try {
                String id = KitRequestShipping.generateBspSampleID(conn, "TestProject_0023", "SALIVA", 1);
                Assert.assertNotNull(id);
                Assert.assertEquals("TestProject_0023_SALIVA", id);
            }
            catch (Exception e) {
                throw new RuntimeException("generateBspSampleID ", e);
            }
            return null;
        });
    }

    @Test
    public void labelNormalUSParticipant() throws Exception {
        DDPParticipant participant = DDPParticipant.getDDPParticipant(DDP_BASE_URL, TEST_DDP, FAKE_BSP_TEST, false);
        Address toAddress = easyPost(INSTANCE_ID, TEST_DDP, participant, false);
        Assert.assertEquals("Karl Lejon", toAddress.getName());
    }

    @Test
    public void labelPepperParticipant() throws Exception {
        DDPParticipant participant = DDPParticipant.getDDPParticipant(DDP_BASE_URL, TEST_DDP, "PepperParticipant", false);
        Address toAddress = easyPost(INSTANCE_ID, TEST_DDP, participant, false);
        Assert.assertEquals("Bob Barker", toAddress.getName());
        Assert.assertNotEquals("Karl Lejon", toAddress.getName());
    }

    @Test
    public void labelPepperParticipantMailToNameNull() throws Exception {
        DDPParticipant participant = DDPParticipant.getDDPParticipant(DDP_BASE_URL, TEST_DDP, "OtherPepperParticipant", false);
        Address toAddress = easyPost(INSTANCE_ID, TEST_DDP, participant, false);
        Assert.assertEquals("Karl Lejon", toAddress.getName());
        Assert.assertNotEquals("Bob Barker", toAddress.getName());
    }

    @Test
    public void cheapLabel() throws Exception {
        DDPParticipant participant = DDPParticipant.getDDPParticipant(DDP_BASE_URL, TEST_DDP_MIGRATED, "CheapLabelPT", false);
        Address toAddress = easyPost(INSTANCE_ID_MIGRATED, TEST_DDP_MIGRATED, participant, true);
        Assert.assertEquals("Karl Lejon", toAddress.getName());
    }

    @Test
    @Ignore ("No shipping to PO Box participants")
    public void labelPOBoxUSParticipant() throws Exception {
        DDPParticipant participant = DDPParticipant.getDDPParticipant(DDP_BASE_URL, TEST_DDP, "POBOX", false);
        easyPost(INSTANCE_ID, TEST_DDP, participant, false);
    }

    @Test
    //    @Ignore ("No shipping to Canadian participants")
    public void labelCAParticipant() throws Exception {
        DDPParticipant participant = DDPParticipant.getDDPParticipant(DDP_BASE_URL, TEST_DDP, "CA", false);
        easyPost(INSTANCE_ID, TEST_DDP, participant, false);
    }

    public Address easyPost(String instanceId, String instanceName, DDPParticipant participant, boolean notFEDEX2DAY) throws Exception {
        HashMap<Integer, KitRequestSettings> carrierServiceMap = KitRequestSettings.getKitRequestSettings(instanceId);
        EasyPostUtil easyPostUtil = new EasyPostUtil(instanceName);
        Address toAddress = easyPostUtil.createAddress(participant, "617-714-8952");
        Address returnAddress = easyPostUtil.createBroadAddress("Broad Institute", "320 Charles St - Lab 181", "Attn. Broad Genomics",
                "Cambridge", "02141", "MA", "US", "617-714-8952");
        Parcel parcel = easyPostUtil.createParcel("3.2", "6.9", "1.3", "5.2");
        CustomsInfo customs = null;
        if (!"US".equals(participant.getCountry())) {
            customs = easyPostUtil.createCustomsInfo(CUSTOMS_JSON);
        }
        KitRequestSettings kitRequestSettings = carrierServiceMap.get(1);
        if (kitRequestSettings.getCarrierTo() != null) {
            double start = System.currentTimeMillis();
            try {
                Shipment shipment2Participant = easyPostUtil.buyShipment(kitRequestSettings.getCarrierTo(),
                        kitRequestSettings.getCarrierToId(),
                        kitRequestSettings.getServiceTo(), toAddress, returnAddress, parcel, FAKE_BILLING_REF, customs);
                double end = System.currentTimeMillis();
                logger.info("took " + (end - start) / 1000 + " seconds");
                if (notFEDEX2DAY) {
                    Assert.assertNotEquals("FEDEX_2_DAY", shipment2Participant.getSelectedRate().getService());
                }
                else {
                    Assert.assertEquals("FEDEX_2_DAY", shipment2Participant.getSelectedRate().getService());
                }
                printShipmentInfos(shipment2Participant);
                doAsserts(shipment2Participant);
            }
            catch (RateNotAvailableException e) {
                Assert.fail("Rate was not available " + e.getMessage());
            }
        }
        else {
            //at least the label to participants must be bought, otherwise nothing would be testes, so throw an error!
            Assert.fail();
        }
        // that label is not really needed, because at the moment we are not buying return labels
        if (kitRequestSettings.getCarrierReturn() != null && kitRequestSettings.getServiceReturn() != null) {
            Shipment returnShipment = easyPostUtil.buyShipment(kitRequestSettings.getCarrierReturn(),
                    kitRequestSettings.getCarrierReturnId(), kitRequestSettings.getServiceReturn(),
                    returnAddress, returnAddress, parcel, FAKE_BILLING_REF, customs);
            printShipmentInfos(returnShipment);
            doAsserts(returnShipment);
        }
        return toAddress;
    }

    private void doAsserts(Shipment shipment) {
        if (shipment != null && shipment.getPostageLabel() != null) {
            Assert.assertNotNull(shipment);
            Assert.assertNotNull(shipment.getPostageLabel().getLabelUrl());
            Assert.assertNotNull(shipment.getId());
            logger.info(shipment.getPostageLabel().getLabelUrl());
        }
    }

    private void printShipmentInfos(Shipment shipment) {
        if (shipment != null && shipment.getRates() != null) {
            for (Rate rate : shipment.getRates()) {
                logger.info(rate.getCarrier());
                logger.info(rate.getService());
                logger.info("" + rate.getRate());
                logger.info(rate.id);
            }
            logger.info(shipment.getLabelUrl());
        }
    }

    @Test
    public void TestRollbackOfAddingKitRequest() {
        DBTestUtil.deleteAllKitData(FAKE_DDP_PARTICIPANT_ID);
        DBTestUtil.insertLatestKitRequest(cfg.getString("portal.insertKitRequest"), cfg.getString("portal.insertKit"),
                "", 1, INSTANCE_ID);
        //change table, so that insert of kit fails
        DBTestUtil.executeQuery("update ddp_kit set scan_date = 2 where scan_date is null");
        DBTestUtil.executeQuery("ALTER TABLE `ddp_kit` CHANGE COLUMN `scan_date` `scan_date` BIGINT(20) NOT NULL ;");

        // Getting the latestKitRequest from DDP and writing into ddp_kit_request
        try {
            List<LatestKitRequest> latestKitRequests = new ArrayList<>();
            latestKitRequests.add(new LatestKitRequest(TestHelper.FAKE_DDP_PARTICIPANT_ID, INSTANCE_ID, TEST_DDP, DDP_BASE_URL,
                    "TestProject", false, false, false, false, null));
            ddpKitRequest.requestAndWriteKitRequests(latestKitRequests);
        }
        catch (Exception e) {
            //will throw exception, because it can't write into db anymore, but that is ok!!! :)
            logger.info("Previous Error was wanted!!!");
        }
        //reset table to beginning state
        DBTestUtil.executeQuery("ALTER TABLE `ddp_kit` CHANGE COLUMN `scan_date` `scan_date` BIGINT(20) DEFAULT NULL ;");
        DBTestUtil.executeQuery("update ddp_kit set scan_date = NULL where scan_date = 2");

        // Checking if responded DDPKitRequest is NOT in db
        Assert.assertFalse(DBTestUtil.checkIfValueExists(DBTestUtil.CHECK_KITREQUEST, "FAKE_NEWER_KIT"));
    }

    @Test
    public void bspCollaboratorSampleIdRGPStyle() {
        DBTestUtil.deleteAllKitData("1_3");
        DDPInstance ddpInstance = DDPInstance.getDDPInstance(TEST_DDP);

        HashMap<String, KitType> kitTypes = org.broadinstitute.dsm.model.KitType.getKitLookup();
        KitType kitType = kitTypes.get("TEST" + "_" + INSTANCE_ID);
        if (kitType == null) {
            throw new RuntimeException("KitType unknown");
        }
        HashMap<Integer, KitRequestSettings> carrierServiceTypes = KitRequestSettings.getKitRequestSettings(INSTANCE_ID);
        KitRequestSettings kitRequestSettings = carrierServiceTypes.get(kitType.getKitTypeId());

        inTransaction((conn) -> {
            try {
                String bspCollaboratorSampleType = "TEST";
                if (kitRequestSettings.getCollaboratorSampleTypeOverwrite() != null) {
                    bspCollaboratorSampleType = kitRequestSettings.getCollaboratorSampleTypeOverwrite();
                }
                String collaboratorSampleId = KitRequestShipping.generateBspSampleID(conn, "TestProject_1_3", bspCollaboratorSampleType, kitType.getKitTypeId());
                Assert.assertNotNull(collaboratorSampleId);
                Assert.assertEquals("TestProject_1_3", collaboratorSampleId);
            }
            catch (Exception e) {
                throw new RuntimeException("generateBspSampleID ", e);
            }
            return null;
        });
    }

    @Test
    public void bspCollaboratorSampleIdRGPStyleMultipleKits() throws Exception {
        DBTestUtil.deleteAllKitData("1_3");
        Map<String, KitType> kitTypes = KitType.getKitLookup();
        String key = "SALIVA_" + INSTANCE_ID;
        KitType kitType = kitTypes.get(key);
        //upload kits for one type
        String csvContent = TestUtil.readFile("KitUploadTestDDP.txt");
        HttpResponse response = TestUtil.perform(Request.Post(DSM_BASE_URL + "/ui/" + "kitUpload?realm=" + TEST_DDP + "&kitType=SALIVA&userId=26"), csvContent, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        List<String> strings = new ArrayList<>();
        strings.add(TEST_DDP);
        strings.add(String.valueOf(kitType.getKitTypeId()));
        strings.add("1_3"); //Mother
        String bspCollaboratorIdMotherKit1 = DBTestUtil.getStringFromQuery(KIT_QUERY, strings, "bsp_collaborator_sample_id");
        Assert.assertEquals("TestProject_01_3_SALIVA", bspCollaboratorIdMotherKit1);

        //upload same kits for another type
        String otherKitType = "TEST";
        key = otherKitType + "_" + INSTANCE_ID;
        kitType = kitTypes.get(key);
        response = TestUtil.perform(Request.Post(DSM_BASE_URL + "/ui/" + "kitUpload?realm=" + TEST_DDP + "&kitType=" + otherKitType + "&userId=26"), csvContent, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        strings = new ArrayList<>();
        strings.add(TEST_DDP);
        strings.add(String.valueOf(kitType.getKitTypeId()));
        strings.add("1_3"); //Mother
        String bspCollaboratorIdMotherKit2 = DBTestUtil.getStringFromQuery(KIT_QUERY, strings, "bsp_collaborator_sample_id");
        Assert.assertEquals("TestProject_1_3", bspCollaboratorIdMotherKit2);
    }

    @Test
    //double check that your user 26 has role kit_upload for osteo
    public void uploadOsteoBloodKitAOM() throws Exception {
        //upload kits for one type
        String csvContent = TestUtil.readFile("SpecialKitOsteo.txt");
        HttpResponse response = TestUtil.perform(Request.Post(DSM_BASE_URL + "/ui/" + "kitUpload?realm=osteo&kitType=BLOOD&userId=26"), csvContent, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        //TODO check db for kit
    }

    @Test
    public void differentReturnPhoneAddress() throws Exception {
        //upload kits for one type
        String csvContent = TestUtil.readFile("KitUploadTestDDP.txt");
        HttpResponse response = TestUtil.perform(Request.Post(DSM_BASE_URL + "/ui/" + "kitUpload?realm=" + TEST_DDP + "&kitType=TEST&userId=26"), csvContent, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        //upload kits for one type
        csvContent = TestUtil.readFile("KitUploadTestDDP.txt");
        response = TestUtil.perform(Request.Post(DSM_BASE_URL + "/ui/" + "kitUpload?realm=" + TEST_DDP + "&kitType=SALIVA&userId=26"), csvContent, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        triggerLabelCreationAndWaitForLabel(TEST_DDP, null, 60);

        Map<String, KitType> kitTypes = KitType.getKitLookup();
        String key = "TEST_" + INSTANCE_ID;
        KitType kitType = kitTypes.get(key);

        List<String> strings = new ArrayList<>();
        strings.add(TEST_DDP);
        strings.add(String.valueOf(kitType.getKitTypeId()));
        strings.add("1_3");
        String easypostToId = DBTestUtil.getStringFromQuery(KIT_QUERY, strings, "easypost_to_id");
        Assert.assertNotNull(easypostToId); //changed from before were label was bought when kitrequest was added to db
        EasyPostUtil easyPostUtil = new EasyPostUtil(TEST_DDP);
        Shipment shipment = easyPostUtil.getShipment(easypostToId);
        Address address = shipment.getFromAddress();
        Assert.assertEquals("6177147395", address.getPhone());

        key = "SALIVA_" + INSTANCE_ID;
        kitType = kitTypes.get(key);

        strings = new ArrayList<>();
        strings.add(TEST_DDP);
        strings.add(String.valueOf(kitType.getKitTypeId()));
        strings.add("1_3");
        easypostToId = DBTestUtil.getStringFromQuery(KIT_QUERY, strings, "easypost_to_id");
        Assert.assertNotNull(easypostToId);
        shipment = easyPostUtil.getShipment(easypostToId);
        address = shipment.getFromAddress();
        Assert.assertEquals("6177148952", address.getPhone());
    }

    public static void triggerLabelCreationAndWaitForLabel(String ddp, String kitType, long waitSeconds) {
        // set all kits of the realm to generate a label
        KitRequestCreateLabel.updateKitLabelRequested(ddp, kitType, "1", new DDPInstanceDto.Builder().build());
        //start method of label job
        List<KitRequestCreateLabel> kitsLabelTriggered = KitUtil.getListOfKitsLabelTriggered();
        if (!kitsLabelTriggered.isEmpty()) {
            KitUtil.createLabel(kitsLabelTriggered);
            // wait for labels to get created
            try {
                Thread.sleep(waitSeconds * 1000L);//20 sec
            }
            catch (Exception e) {
                throw new RuntimeException("something went wrong, while waiting for quartz job to finish...", e);
            }
        }
    }

    @Test
    public void removeTrailingSpaces() throws Exception {
        //upload kits for one type
        String csvContent = TestUtil.readFile("KitUploadTestDDP.txt");
        HttpResponse response = TestUtil.perform(Request.Post(DSM_BASE_URL + "/ui/" + "kitUpload?realm=" + TEST_DDP + "&kitType=TEST&userId=26"), csvContent, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        Map<String, KitType> kitTypes = KitType.getKitLookup();
        String key = "TEST_" + INSTANCE_ID;
        KitType kitType = kitTypes.get(key);

        List<String> strings = new ArrayList<>();
        strings.add(TEST_DDP);
        strings.add(String.valueOf(kitType.getKitTypeId()));
        strings.add("1_3"); //Mother
        String ddpParticipantId = DBTestUtil.getStringFromQuery(KIT_QUERY, strings, "ddp_participant_id");
        Assert.assertEquals("1_3", ddpParticipantId);
    }

    @Test
    public void triggerBloodReminders() {
        DBTestUtil.insertLatestKitRequest(cfg.getString("portal.insertKitRequest"), cfg.getString("portal.insertKit"), EVENT_UTIL_TEST, 2,
                INSTANCE_ID);
        DBTestUtil.setKitToSent("FAKE_KIT_" + EVENT_UTIL_TEST, "FAKE_DSM_LABEL_UID" + EVENT_UTIL_TEST, System.currentTimeMillis());

        DBTestUtil.insertLatestKitRequest(cfg.getString("portal.insertKitRequest"), cfg.getString("portal.insertKit"), EVENT_UTIL_TEST + "2",
                2, INSTANCE_ID);
        DBTestUtil.setKitToSent("FAKE_KIT_" + EVENT_UTIL_TEST + "2", "FAKE_DSM_LABEL_UID" + EVENT_UTIL_TEST + "2", System.currentTimeMillis() - (17 * DBTestUtil.WEEK));

        eventUtil.triggerReminder();

        //check kit which is just 2 weeks old is not in event table
        ArrayList<String> strings = new ArrayList<>();
        strings.add(FAKE_LATEST_KIT + EVENT_UTIL_TEST);
        strings.add(FAKE_DDP_PARTICIPANT_ID + EVENT_UTIL_TEST);
        String kitId = DBTestUtil.getStringFromQuery(RouteTest.SELECT_KITREQUEST_QUERY, strings, "dsm_kit_request_id");
        Assert.assertNull(DBTestUtil.getQueryDetail("select * from EVENT_QUEUE WHERE DSM_KIT_REQUEST_ID = ?", kitId, "EVENT_ID"));

        //check kit which is now 17 weeks old is NOT event table
        strings = new ArrayList<>();
        strings.add(FAKE_LATEST_KIT + EVENT_UTIL_TEST + "2");
        strings.add(FAKE_DDP_PARTICIPANT_ID + EVENT_UTIL_TEST + "2");
        kitId = DBTestUtil.getStringFromQuery(RouteTest.SELECT_KITREQUEST_QUERY, strings, "dsm_kit_request_id");
        Assert.assertNull(DBTestUtil.getQueryDetail("select * from EVENT_QUEUE WHERE DSM_KIT_REQUEST_ID = ? AND EVENT_TYPE='BLOOD_SENT_4WK'", kitId, "EVENT_ID"));

        //check kit which is now 18 weeks old is in event table
        DBTestUtil.setKitToSent("FAKE_KIT_" + EVENT_UTIL_TEST + "2", "FAKE_DSM_LABEL_UID" + EVENT_UTIL_TEST + "2", System.currentTimeMillis() - (18 * DBTestUtil.WEEK));
        eventUtil.triggerReminder();
        Assert.assertNotNull(DBTestUtil.getQueryDetail("select * from EVENT_QUEUE WHERE DSM_KIT_REQUEST_ID = ? AND EVENT_TYPE='BLOOD_SENT_4WK'", kitId, "EVENT_ID"));
    }

    @Test
    public void skipDDPEventBloodReminder() {
        DBTestUtil.insertLatestKitRequest(cfg.getString("portal.insertKitRequest"), cfg.getString("portal.insertKit"),
                "_tt3", 2, INSTANCE_ID);
        DBTestUtil.setKitToSent("FAKE_SPK_UUID_tt3", "FAKE_DSM_LABEL_UID_tt3", System.currentTimeMillis() - (18 * DBTestUtil.WEEK));
        DBTestUtil.executeQuery("insert into ddp_participant_event (event, ddp_participant_id, ddp_instance_id, date, done_by) values (\'BLOOD_SENT_2WK\', \'" + FAKE_DDP_PARTICIPANT_ID + "_tt3" + "\', \'" + INSTANCE_ID + "\', \'" + System.currentTimeMillis() + "\', \'1\')");

        eventUtil.triggerReminder();

        //check if event_triggered = 1, which would mean it was sent
        Assert.assertNull(DBTestUtil.getQueryDetail("select * from EVENT_QUEUE queue, ddp_kit_request request where queue.DSM_KIT_REQUEST_ID = request.dsm_kit_request_id and request.ddp_label = ? and EVENT_TYPE='BLOOD_SENT_2WK' AND queue.EVENT_TRIGGERED = 1", "FAKE_DSM_LABEL_UID_tt3", "EVENT_ID"));
        Assert.assertNotNull(DBTestUtil.getQueryDetail("select * from EVENT_QUEUE queue, ddp_kit_request request where queue.DSM_KIT_REQUEST_ID = request.dsm_kit_request_id and request.ddp_label = ? and EVENT_TYPE='BLOOD_SENT_4WK' AND queue.EVENT_TRIGGERED = 1", "FAKE_DSM_LABEL_UID_tt3", "EVENT_ID"));
    }

    @Test
    public void skipDDPEventBloodReceived() throws Exception {
        DBTestUtil.insertLatestKitRequest(cfg.getString("portal.insertKitRequest"), cfg.getString("portal.insertKit"),
                "_tt4", 2, INSTANCE_ID);
        DBTestUtil.setKitToSent("FAKE_SPK_UUID_tt4", "FAKE_DSM_LABEL_UID_tt4", System.currentTimeMillis() - (3 * DBTestUtil.WEEK));
        DBTestUtil.executeQuery("insert into ddp_participant_event (event, ddp_participant_id, ddp_instance_id, date, done_by) values (\'BLOOD_RECEIVED\', \'" + FAKE_DDP_PARTICIPANT_ID + "_tt4" + "\', \'" + INSTANCE_ID + "\', \'" + System.currentTimeMillis() + "\', \'1\')");

        HttpResponse response = bspKit("FAKE_SPK_UUID_tt4");
        Gson gson = new GsonBuilder().create();
        KitInfo bspMetaData = gson.fromJson(DDPRequestUtil.getContentAsString(response), KitInfo.class);
        Assert.assertEquals("FAKE_BSP_COLL_ID_tt4", bspMetaData.getCollaboratorParticipantId());
        Assert.assertEquals("FAKE_BSP_SAM_ID_tt4", bspMetaData.getCollaboratorSampleId());
        Assert.assertEquals("U", bspMetaData.getGender());
        Assert.assertEquals("Whole Blood:Streck Cell-Free Preserved", bspMetaData.getMaterialInfo());
        Assert.assertEquals("Vacutainer Cell-Free DNA Tube Camo-Top [10mL]", bspMetaData.getReceptacleName());
        Assert.assertEquals("SC-123", bspMetaData.getSampleCollectionBarcode());
        Assert.assertEquals(1, bspMetaData.getOrganismClassificationId());
        //check if event_triggered = 1, which would mean it was sent
        Assert.assertNull(DBTestUtil.getQueryDetail("select * from EVENT_QUEUE queue, ddp_kit_request request where queue.DSM_KIT_REQUEST_ID = request.dsm_kit_request_id and request.ddp_label = ? AND queue.EVENT_TRIGGERED = 1", "FAKE_DSM_LABEL_UID_tt4", "EVENT_ID"));
    }

    @Test
    public void bspNormalKit() throws Exception {
        HttpResponse response = bspKit("testing123");
        Gson gson = new GsonBuilder().create();
        KitInfo bspMetaData = gson.fromJson(DDPRequestUtil.getContentAsString(response), KitInfo.class);
        Assert.assertEquals("JJGUNZLLAY6HK9AAFWZQ", bspMetaData.getCollaboratorParticipantId());
        Assert.assertEquals("VTLI45RTA3VGZRJBssfQ7", bspMetaData.getCollaboratorSampleId());
        Assert.assertEquals("U", bspMetaData.getGender());
        Assert.assertEquals("Saliva", bspMetaData.getMaterialInfo());
        Assert.assertEquals("Oragene Kit", bspMetaData.getReceptacleName());
        Assert.assertEquals("SC-123", bspMetaData.getSampleCollectionBarcode());
        Assert.assertEquals(1, bspMetaData.getOrganismClassificationId());
    }

    @Test
    public void bspDeactivatedKit() throws Exception {
        HttpResponse response = bspKit("deactivatedK");
        Gson gson = new GsonBuilder().create();
        BSPKitStatus bspStatus = gson.fromJson(DDPRequestUtil.getContentAsString(response), BSPKitStatus.class);
        Assert.assertEquals("DEACTIVATED", bspStatus.getStatus());
    }

    @Test
    public void bspExitedKit() throws Exception {
        HttpResponse response = bspKit("exitedK");
        Gson gson = new GsonBuilder().create();
        BSPKitStatus bspStatus = gson.fromJson(DDPRequestUtil.getContentAsString(response), BSPKitStatus.class);
        Assert.assertEquals("EXITED", bspStatus.getStatus());
    }

    private HttpResponse bspKit(@NonNull String kitLabel) throws Exception {
        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, "/api/" + "Kits/" + kitLabel, testUtil.buildHeaders(cfg.getString("bsp.secret"))).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        return response;
    }

    @Test
    public void bspBadKit() throws Exception {
        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, "/api/" + "Kits/badK", testUtil.buildHeaders(cfg.getString("bsp.secret"))).returnResponse();
        Assert.assertEquals(404, response.getStatusLine().getStatusCode());
    }

    @Test
    public void labelCreation() throws Exception {
        String csvContent = TestUtil.readFile("KitUpload.txt");
        HttpResponse response = TestUtil.perform(Request.Post(DSM_BASE_URL + "/ui/" + "kitUpload?realm=" + TEST_DDP + "&kitType=BLOOD&userId=26"), csvContent, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        triggerLabelCreationAndWaitForLabel(TEST_DDP, "BLOOD", 30);

        //check if there is a shipment id
        String testValue = DBTestUtil.getQueryDetail(DBTestUtil.CHECK_KIT, "8899777882", "easypost_to_id");
        Assert.assertNotNull(testValue);
        testValue = DBTestUtil.getQueryDetail(DBTestUtil.CHECK_KIT, "9889938837", "easypost_to_id");
        Assert.assertNotNull(testValue);
        testValue = DBTestUtil.getQueryDetail(DBTestUtil.CHECK_KIT, "33837748499", "easypost_to_id");
        Assert.assertNotNull(testValue);
        testValue = DBTestUtil.getQueryDetail(DBTestUtil.CHECK_KIT, "9384658393", "easypost_to_id");
        Assert.assertNotNull(testValue);
        //was not valid address -> is not in db
        testValue = DBTestUtil.getQueryDetail(DBTestUtil.CHECK_KIT, "333998474", "easypost_to_id");
        Assert.assertNull(testValue);
        //was too long to make collaboratorId
        //canadian and po box
        testValue = DBTestUtil.getQueryDetail(DBTestUtil.CHECK_KIT, "33839457566", "easypost_to_id");
        Assert.assertNull(testValue);
        testValue = DBTestUtil.getQueryDetail(DBTestUtil.CHECK_KIT, "3384948", "easypost_to_id");
        Assert.assertNull(testValue);

        //check that error message is written
        //333998474 was not valid address and is therefore not in db (also no message there...)
        testValue = DBTestUtil.getQueryDetail(DBTestUtil.CHECK_KIT, "33839457566", "message");
        Assert.assertNotNull(testValue);
        testValue = DBTestUtil.getQueryDetail(DBTestUtil.CHECK_KIT, "3384948", "message");
        Assert.assertNotNull(testValue);

        //check if there is a label
        testValue = DBTestUtil.getQueryDetail(DBTestUtil.CHECK_KIT, "8899777882", "label_url_to");
        Assert.assertNotNull(testValue);
        testValue = DBTestUtil.getQueryDetail(DBTestUtil.CHECK_KIT, "9889938837", "label_url_to");
        Assert.assertNotNull(testValue);
        testValue = DBTestUtil.getQueryDetail(DBTestUtil.CHECK_KIT, "33837748499", "label_url_to");
        Assert.assertNotNull(testValue);
        testValue = DBTestUtil.getQueryDetail(DBTestUtil.CHECK_KIT, "9384658393", "label_url_to");
        Assert.assertNotNull(testValue);
    }

    private void checkError(String participant) {
        //throw exception so check if error and message at kit is set
        String error = DBTestUtil.getQueryDetail(KIT_QUERY_BY_PARTICIPANT, participant, "error");
        String message = DBTestUtil.getQueryDetail(KIT_QUERY_BY_PARTICIPANT, participant, "message");
        Assert.assertEquals("1", error);
        Assert.assertNotNull(message);
    }

    @Test
    public void multipleFedExAccountsKitQuartzDDPRequest() throws Exception {
        String message = TestUtil.readFile("ddpResponses/KitRequestsSingle.json");
        mockDDP.when(
                request().withPath("/ddp/kitrequests/" + "TEST_MULTIPLE_FEDEX_QUARTZ"))
                .respond(response().withStatusCode(200).withBody(message));

        message = TestUtil.readFile("ddpResponses/ParticipantsWithIdMULTIPLE_ACCOUNT_TEST.json");
        mockDDP.when(
                request().withPath("/ddp/participants/MULTIPLE_ACCOUNT_TEST"))
                .respond(response().withStatusCode(200).withBody(message));

        // Getting all KitRequests (because of the null --> getting whole list)
        List<LatestKitRequest> latestKitRequests = new ArrayList<>();
        latestKitRequests.add(new LatestKitRequest("TEST_MULTIPLE_FEDEX_QUARTZ", INSTANCE_ID_2, TEST_DDP_2, DDP_BASE_URL,
                DBTestUtil.getQueryDetail(DBUtil.GET_REALM_QUERY, TEST_DDP_2, DBConstants.COLLABORATOR_ID_PREFIX), false, false, false, false, null));
        ddpKitRequest.requestAndWriteKitRequests(latestKitRequests);

        checkKitShipmentIdEmpty("MULTIPLE_ACCOUNT_TEST", "4");
        checkKitShipmentIdEmpty("MULTIPLE_ACCOUNT_TEST", "5");

        triggerLabelCreationAndWaitForLabel(TEST_DDP_2, null, 40);

        checkKitShipmentId(TEST_DDP_2, "MULTIPLE_ACCOUNT_TEST", "4", "ca_d0ca5b309b6a4948b3275b6f8e784cd3");

        checkKitShipmentId(TEST_DDP_2, "MULTIPLE_ACCOUNT_TEST", "5", "ca_67f7b4205e3a4b1a91b90232c40ce58f");
    }

    @Test
    public void multipleFedExAccountsKitUpload() throws Exception {
        String csvContent = TestUtil.readFile("KitUploadAnotherDDP.txt");
        HttpResponse response = TestUtil.perform(Request.Post(DSM_BASE_URL + "/ui/" + "kitUpload?realm=" + TEST_DDP_2 + "&kitType=SHIPPING-1&userId=26"), csvContent, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        csvContent = TestUtil.readFile("KitUploadAnotherDDP2.txt");
        response = TestUtil.perform(Request.Post(DSM_BASE_URL + "/ui/" + "kitUpload?realm=" + TEST_DDP_2 + "&kitType=SHIPPING-2&userId=26"), csvContent, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        checkKitShipmentIdEmpty("MULTIPLE_ACCOUNT_TEST_666", "4");
        checkKitShipmentIdEmpty("MULTIPLE_ACCOUNT_TEST_667", "4");

        checkKitShipmentIdEmpty("MULTIPLE_ACCOUNT_TEST_1666", "5");
        checkKitShipmentIdEmpty("MULTIPLE_ACCOUNT_TEST_1667", "5");

        triggerLabelCreationAndWaitForLabel(TEST_DDP_2, null, 40);

        //check if participant in kit request table
        checkKitShipmentId(TEST_DDP_2, "MULTIPLE_ACCOUNT_TEST_666", "4", "ca_d0ca5b309b6a4948b3275b6f8e784cd3");
        checkKitShipmentId(TEST_DDP_2, "MULTIPLE_ACCOUNT_TEST_667", "4", "ca_d0ca5b309b6a4948b3275b6f8e784cd3");

        //check if participant in kit request table
        checkKitShipmentId(TEST_DDP_2, "MULTIPLE_ACCOUNT_TEST_1666", "5", "ca_67f7b4205e3a4b1a91b90232c40ce58f");
        checkKitShipmentId(TEST_DDP_2, "MULTIPLE_ACCOUNT_TEST_1667", "5", "ca_67f7b4205e3a4b1a91b90232c40ce58f");
    }

    private void checkKitShipmentIdEmpty(String participantId, String kitId) {
        Assert.assertTrue(DBTestUtil.checkIfValueExists(DBTestUtil.CHECK_KIT_REQUEST, participantId));

        ArrayList<String> strings = new ArrayList<>();
        strings.add(participantId);
        strings.add(kitId);

        String shipmentId = DBTestUtil.getStringFromQuery(DBTestUtil.CHECK_KIT_BY_TYPE, strings, "easypost_to_id");
        Assert.assertNull(shipmentId);
    }

    private void checkKitShipmentId(String ddpName, String participantId, String kitId, String carrierId) throws Exception {
        Assert.assertTrue(DBTestUtil.checkIfValueExists(DBTestUtil.CHECK_KIT_REQUEST, participantId));

        ArrayList<String> strings = new ArrayList<>();
        strings.add(participantId);
        strings.add(kitId);

        String shipmentId = DBTestUtil.getStringFromQuery(DBTestUtil.CHECK_KIT_BY_TYPE, strings, "easypost_to_id");
        Assert.assertNotNull(shipmentId);
        EasyPostUtil easyPostUtil = new EasyPostUtil(ddpName);
        Shipment shipment = easyPostUtil.getShipment(shipmentId);
        Rate rate = shipment.getSelectedRate();
        Assert.assertEquals(carrierId, rate.getCarrierAccountId());
    }

    @Test
    public void multipleFedExAccountsKitExpress() throws Exception {
        expressKitAnotherDDP("_EXPRESS_1", "4", "ca_d0ca5b309b6a4948b3275b6f8e784cd3");
        expressKitAnotherDDP("_EXPRESS_2", "5", "ca_67f7b4205e3a4b1a91b90232c40ce58f");
    }

    private void expressKitAnotherDDP(String suffix, String kitId, String carrierId) throws Exception {
        DBTestUtil.insertLatestKitRequest(cfg.getString("portal.insertKitRequest"), cfg.getString("portal.insertKit"), suffix,
                Integer.parseInt(kitId), INSTANCE_ID_2);
        ArrayList strings = new ArrayList<>();
        strings.add(FAKE_LATEST_KIT + suffix);
        strings.add(FAKE_DDP_PARTICIPANT_ID + suffix);
        String kitRequestId = DBTestUtil.getStringFromQuery(SELECT_KITREQUEST_QUERY, strings, "dsm_kit_request_id");

        HttpResponse response = TestUtil.perform(Request.Patch(DSM_BASE_URL + "/ui/" + "expressKit/" + kitRequestId + "?userId=26"), null, testUtil.buildAuthHeaders()).returnResponse();

        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        Assert.assertEquals("2", DBTestUtil.getQueryDetail("SELECT count(*) from ddp_kit where dsm_kit_request_id = ?", kitRequestId, "count(*)"));
        Assert.assertEquals("Generated Express", DBTestUtil.getQueryDetail("SELECT * from ddp_kit where dsm_kit_request_id = ? order by dsm_kit_id limit 1 ", kitRequestId, "deactivation_reason"));
        String testValue = DBTestUtil.getQueryDetail("SELECT * from ddp_kit where dsm_kit_request_id = ? order by dsm_kit_id desc limit 1 ", kitRequestId, "label_url_to");
        Assert.assertNotNull(testValue);

        checkKitShipmentId(TEST_DDP_2, FAKE_DDP_PARTICIPANT_ID + suffix, kitId, carrierId);
    }

    @Test
    public void differentKitTypesSameName() throws Exception {
        bspEndpoint(INSTANCE_ID, 2, "_BLOOD_TEST_DDP", "Whole Blood:Streck Cell-Free Preserved", "Vacutainer Cell-Free DNA Tube Camo-Top [10mL]", "SC-123");
        bspEndpoint(INSTANCE_ID_2, 6, "_BLOOD_ANOTHER_DDP", "Whole Blood: Paxgene Preserved", "Vacutainer EDTA Tube Purple-Top [10mL]", "SC-12333");
    }

    @Test
    public void changeLabelSetting() throws Exception {
        //add labelSetting
        addLabelSetting();

        //get labelId
        String labelId = DBTestUtil.getStringFromQuery("select * from label_setting where name = \"Avery1\"", null, "label_setting_id");

        //change value of label setting
        String json = "[{\"labelSettingId\":\"" + labelId + "\", \"name\": \"Avery1.0\", \"description\": \"testLabel changed Name\", \"defaultPage\": false, \"labelOnPage\": 4, \"labelHeight\": 10.0, \"labelWidth\": 8.0, \"topMargin\": 1, \"rightMargin\": 1.0, \"bottomMargin\": 8.0, \"leftMargin\": 1.0, \"spaceBetweenLabelsLeftRight\": 1, \"spaceBetweenLabelsTopBottom\": 1.0}]";
        HttpResponse response = TestUtil.perform(Request.Patch(DSM_BASE_URL + "/ui/labelSettings"), json, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        //check changed value
        Assert.assertEquals("Avery1.0", DBTestUtil.getStringFromQuery("select * from label_setting where label_setting_id = " + labelId, null, "name"));

        //delete label again
        DBTestUtil.executeQuery("DELETE FROM label_setting WHERE label_setting_id = " + labelId);
    }

    public void addLabelSetting() throws Exception {
        //add label setting
        String json = "[{\"name\": \"Avery1\", \"description\": \"testLabel\", \"defaultPage\": false, \"labelOnPage\": 4, \"labelHeight\": 10.0, \"labelWidth\": 8.0, \"topMargin\": 1, \"rightMargin\": 1.0, \"bottomMargin\": 8.0, \"leftMargin\": 1.0, \"spaceBetweenLabelsLeftRight\": 1, \"spaceBetweenLabelsTopBottom\": 1.0}]";
        HttpResponse response = TestUtil.perform(Request.Patch(DSM_BASE_URL + "/ui/labelSettings"), json, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        //get last changed on with that combination to get the one just added
        Assert.assertEquals("testLabel", DBTestUtil.getStringFromQuery("select * from label_setting where name = \"Avery1\"", null, "description"));
    }

    @Test
    public void labelSettings() throws Exception {
        //add labelSetting
        addLabelSetting();

        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, "/ui/" + "labelSettings", testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        Gson gson = new GsonBuilder().create();
        LabelSettings[] labelSettings = gson.fromJson(DDPRequestUtil.getContentAsString(response), LabelSettings[].class);
        Assert.assertEquals(String.valueOf(labelSettings.length), DBTestUtil.getStringFromQuery("select count(*) from label_setting where not (deleted <=> 1)", null, "count(*)"));

        //get labelId
        String labelId = DBTestUtil.getStringFromQuery("select * from label_setting where name = \"Avery1\"", null, "label_setting_id");

        //delete label again
        DBTestUtil.executeQuery("DELETE FROM label_setting WHERE label_setting_id = " + labelId);
    }

    @Test
    public void shippingReportDownloadEndpoint() throws Exception {
        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, "/ui/sampleReport?userId=26", testUtil.buildAuthHeaders()).returnResponse();

        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        String message = DDPRequestUtil.getContentAsString(response);
        Gson gson = new GsonBuilder().create();
        KitReport[] kitReports = gson.fromJson(message, KitReport[].class);
        Assert.assertNotNull(kitReports);
        Assert.assertTrue(kitReports.length > 0);
    }

    @Test
    public void shippingReportEndpoint() throws Exception {
        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, "/ui/sampleReport/2017-03-01/2017-03-20?userId=26", testUtil.buildAuthHeaders()).returnResponse();

        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        String message = DDPRequestUtil.getContentAsString(response);
        Gson gson = new GsonBuilder().create();
        KitReport[] kitReports = gson.fromJson(message, KitReport[].class);
        Assert.assertNotNull(kitReports);
        Assert.assertTrue(kitReports.length > 0);
    }

    @Test
    public void expressKitCostAlert() throws Exception {
        DBTestUtil.insertLatestKitRequest(cfg.getString("portal.insertKitRequest"), cfg.getString("portal.insertKit"), "_express",
                1, INSTANCE_ID);
        ArrayList strings = new ArrayList<>();
        strings.add(FAKE_LATEST_KIT + "_express");
        strings.add(FAKE_DDP_PARTICIPANT_ID + "_express");
        String kitRequestId = DBTestUtil.getStringFromQuery(SELECT_KITREQUEST_QUERY, strings, "dsm_kit_request_id");

        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, "/ui/" + "expressKit/" + kitRequestId + "?userId=26", testUtil.buildAuthHeaders()).returnResponse();

        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        String message = DDPRequestUtil.getContentAsString(response);
        Gson gson = new GsonBuilder().create();
        EasypostLabelRate labelRate = gson.fromJson(message, EasypostLabelRate.class);
        Assert.assertNotNull(labelRate);
        Assert.assertNotNull(labelRate.getNormal());
        Assert.assertNotNull(labelRate.getExpress());
    }

    @Test
    public void expressKit() throws Exception {
        DBTestUtil.insertLatestKitRequest(cfg.getString("portal.insertKitRequest"), cfg.getString("portal.insertKit"), "_express2",
                1, INSTANCE_ID);
        ArrayList strings = new ArrayList<>();
        strings.add(FAKE_LATEST_KIT + "_express2");
        strings.add(FAKE_DDP_PARTICIPANT_ID + "_express2");
        String kitRequestId = DBTestUtil.getStringFromQuery(SELECT_KITREQUEST_QUERY, strings, "dsm_kit_request_id");

        HttpResponse response = TestUtil.perform(Request.Patch(DSM_BASE_URL + "/ui/" + "expressKit/" + kitRequestId + "?userId=26"), null, testUtil.buildAuthHeaders()).returnResponse();

        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        Assert.assertEquals("2", DBTestUtil.getQueryDetail("SELECT count(*) from ddp_kit where dsm_kit_request_id = ?", kitRequestId, "count(*)"));
        Assert.assertEquals("Generated Express", DBTestUtil.getQueryDetail("SELECT * from ddp_kit where dsm_kit_request_id = ? order by dsm_kit_id limit 1 ", kitRequestId, "deactivation_reason"));
        String testValue = DBTestUtil.getQueryDetail("SELECT * from ddp_kit where dsm_kit_request_id = ? order by dsm_kit_id desc limit 1 ", kitRequestId, "label_url_to");
        Assert.assertNotNull(testValue);
    }

    @Test
    // will throw error because it won't be able to refund the test shipment (was just added into db and not by creating of a new label,
    // therefore easypost doesn't know anything about that shipment id)
    public void reactivateKitRequest() throws Exception {
        deactivateKitRequest(false, "_deactivate");

        ArrayList strings = new ArrayList<>();
        strings.add(FAKE_LATEST_KIT + "_deactivate");
        strings.add(FAKE_DDP_PARTICIPANT_ID + "_deactivate");
        String kitRequestId = DBTestUtil.getStringFromQuery(SELECT_KITREQUEST_QUERY, strings, "dsm_kit_request_id");
        HttpResponse response = TestUtil.perform(Request.Patch(DSM_BASE_URL + "/ui/" + "activateKit/" + kitRequestId + "?userId=26"), null, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        Assert.assertNull(DBTestUtil.getQueryDetail("select * from(select req.ddp_participant_id, req.dsm_kit_request_id from kit_type kt, ddp_kit_request req, ddp_instance ddp_site where req.ddp_instance_id = ddp_site.ddp_instance_id and req.kit_type_id = kt.kit_type_id) as request left join (select * from (SELECT kit.dsm_kit_request_id, kit.error, kit.message, kit.deactivated_date FROM ddp_kit kit INNER JOIN( SELECT dsm_kit_request_id, MAX(dsm_kit_id) AS kit_id FROM ddp_kit GROUP BY dsm_kit_request_id) groupedKit ON kit.dsm_kit_request_id = groupedKit.dsm_kit_request_id AND kit.dsm_kit_id = groupedKit.kit_id LEFT JOIN ddp_kit_tracking tracking ON (kit.kit_label = tracking.kit_label))as wtf) as kit on kit.dsm_kit_request_id = request.dsm_kit_request_id where request.dsm_kit_request_id = ?", kitRequestId, "deactivated_date"));
    }

    @Test
    // will throw error because it won't be able to refund the test shipment (was just added into db and not by creating of a new label,
    // therefore easypost doesn't know anything about that shipment id)
    public void reactivateKitRequestBeforeLabelCreation() throws Exception {
        deactivateKitRequest(true, "_deactivate2");

        ArrayList strings = new ArrayList<>();
        strings.add(FAKE_LATEST_KIT + "_deactivate2");
        strings.add(FAKE_DDP_PARTICIPANT_ID + "_deactivate2");
        String kitRequestId = DBTestUtil.getStringFromQuery(SELECT_KITREQUEST_QUERY, strings, "dsm_kit_request_id");
        if (StringUtils.isBlank(kitRequestId)) {
            Assert.fail("Didn't find kit");
        }
        HttpResponse response = TestUtil.perform(Request.Patch(DSM_BASE_URL + "/ui/" + "activateKit/" + kitRequestId + "?userId=26"), null, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        Assert.assertNull(DBTestUtil.getQueryDetail("select * from(select req.ddp_participant_id, req.dsm_kit_request_id from kit_type kt, ddp_kit_request req, ddp_instance ddp_site where req.ddp_instance_id = ddp_site.ddp_instance_id and req.kit_type_id = kt.kit_type_id) as request left join (select * from (SELECT kit.dsm_kit_request_id, kit.error, kit.message, kit.deactivated_date FROM ddp_kit kit INNER JOIN( SELECT dsm_kit_request_id, MAX(dsm_kit_id) AS kit_id FROM ddp_kit GROUP BY dsm_kit_request_id) groupedKit ON kit.dsm_kit_request_id = groupedKit.dsm_kit_request_id AND kit.dsm_kit_id = groupedKit.kit_id LEFT JOIN ddp_kit_tracking tracking ON (kit.kit_label = tracking.kit_label))as wtf) as kit on kit.dsm_kit_request_id = request.dsm_kit_request_id where request.dsm_kit_request_id = ?", kitRequestId, "deactivated_date"));
    }

    public void deactivateKitRequest(boolean beforeLabelCreation, String suffix) throws Exception {
        DBTestUtil.insertLatestKitRequest(cfg.getString("portal.insertKitRequest"), cfg.getString("portal.insertKit"), suffix,
                1, INSTANCE_ID);

        if (beforeLabelCreation) {
            triggerLabelCreationAndWaitForLabel(TEST_DDP, null, 20);
        }

        ArrayList strings = new ArrayList<>();
        strings.add(FAKE_LATEST_KIT + suffix);
        strings.add(FAKE_DDP_PARTICIPANT_ID + suffix);
        String kitRequestId = DBTestUtil.getStringFromQuery(SELECT_KITREQUEST_QUERY, strings, "dsm_kit_request_id");

        HttpResponse response = TestUtil.perform(Request.Patch(DSM_BASE_URL + "/ui/" + "deactivateKit/" + kitRequestId + "?userId=26"), "{\"reason\": \"test\"}", testUtil.buildAuthHeaders()).returnResponse();

        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        Assert.assertNotNull(DBTestUtil.getQueryDetail(SELECT_KIT_QUERY, kitRequestId, "deactivated_date"));
    }

    @Test
    public void fileUpload() throws Exception {
        String csvContent = TestUtil.readFile("KitUpload.txt");
        HttpResponse response = TestUtil.perform(Request.Post(DSM_BASE_URL + "/ui/" + "kitUpload?realm=" + TEST_DDP + "&kitType=BLOOD&userId=26"), csvContent, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        //check if participant in kit request table
        Assert.assertTrue(DBTestUtil.checkIfValueExists(DBTestUtil.CHECK_KIT_REQUEST, "8899777882"));
        Assert.assertTrue(DBTestUtil.checkIfValueExists(DBTestUtil.CHECK_KIT_REQUEST, "9889938837"));
        Assert.assertTrue(DBTestUtil.checkIfValueExists(DBTestUtil.CHECK_KIT_REQUEST, "33837748499"));
        Assert.assertTrue(DBTestUtil.checkIfValueExists(DBTestUtil.CHECK_KIT_REQUEST, "33839457566"));
        Assert.assertTrue(DBTestUtil.checkIfValueExists(DBTestUtil.CHECK_KIT_REQUEST, "3384948"));
        Assert.assertTrue(DBTestUtil.checkIfValueExists(DBTestUtil.CHECK_KIT_REQUEST, "9384658393"));
        Assert.assertTrue(!DBTestUtil.checkIfValueExists(DBTestUtil.CHECK_KIT_REQUEST, "333998474")); //wrong address -> not in db!

        //check kitType (one is enough, all are same!)
        Assert.assertEquals("6", DBTestUtil.getQueryDetail(DBTestUtil.CHECK_KIT_REQUEST, "8899777882", "kit_type_id"));

        Assert.assertNotNull(DBTestUtil.getQueryDetail(DBTestUtil.CHECK_KIT, "8899777882", "easypost_address_id_to"));
        Assert.assertNotEquals("", DBTestUtil.getQueryDetail(DBTestUtil.CHECK_KIT, "8899777882", "easypost_address_id_to"));
    }

    @Test
    public void kitRequestEndpoint() throws Exception {
        DBTestUtil.insertLatestKitRequest(cfg.getString("portal.insertKitRequest"), cfg.getString("portal.insertKit"),
                "_kit-1", 1, INSTANCE_ID);
        DBTestUtil.insertLatestKitRequest(cfg.getString("portal.insertKitRequest"), cfg.getString("portal.insertKit"),
                "_kit-2", 1, INSTANCE_ID);

        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, "/ui/kitRequests?realm=" + TEST_DDP + "&target=queue&kitType=SALIVA", testUtil.buildAuthHeaders()).returnResponse();

        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        Assert.assertNotNull(DDPRequestUtil.getContentAsString(response));

        Gson gson = new GsonBuilder().create();
        KitRequestShipping[] kitRequests = gson.fromJson(DDPRequestUtil.getContentAsString(response), KitRequestShipping[].class);
        Assert.assertTrue(kitRequests.length > 0);
        boolean foundKit1 = false;
        String errorMessage = null;
        for (KitRequestShipping kit : kitRequests) {
            if (FAKE_DDP_PARTICIPANT_ID.equals(kit.getParticipantId())) {
                foundKit1 = true;
            }
            if ((FAKE_DDP_PARTICIPANT_ID + "_kit-2").equals(kit.getParticipantId())) {
                errorMessage = kit.getMessage();
            }
        }

        Assert.assertTrue(foundKit1); //is there because mockDDP has a response for that participant
        Assert.assertNotNull(errorMessage); //is not there because mockDDP doesn't have a response to that participant
    }

    @Test
    public void bspKitEndpointWithUnknownLabel() throws Exception {
        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, "/api/Kits/notExisting", testUtil.buildHeaders(cfg.getString("bsp.secret"))).returnResponse();
        Assert.assertEquals(404, response.getStatusLine().getStatusCode());
    }

    @Test
    public void bspEndpointDDPEventSalivaReceived() throws Exception {
        //difference to bspEndpointSkipDDPEventSalivaReceived is that ddp is not setup to sent notifications
        String suffix = "_tt7";
        bspKitEndpointSkipDDPEventTriggered(1, "SALIVA_RECEIVED", suffix, INSTANCE_ID_2);
        Assert.assertNull(DBTestUtil.getQueryDetail("select * from EVENT_QUEUE queue, ddp_kit_request request where queue.DSM_KIT_REQUEST_ID = request.dsm_kit_request_id and request.ddp_label = ?", "FAKE_DSM_LABEL_UID" + suffix, "EVENT_ID"));
    }

    @Test
    public void bspEndpointSkipDDPEventSalivaReceived() throws Exception {
        String suffix = "_tt9";
        bspKitEndpointSkipDDPEventTriggered(1, "SALIVA_RECEIVED", suffix, INSTANCE_ID);
        //check if event_triggered = 1, which would mean it was sent
        Assert.assertNull(DBTestUtil.getQueryDetail("select * from EVENT_QUEUE queue, ddp_kit_request request where queue.DSM_KIT_REQUEST_ID = request.dsm_kit_request_id and request.ddp_label = ? AND queue.EVENT_TRIGGERED = 1", "FAKE_DSM_LABEL_UID" + suffix, "EVENT_ID"));
    }

    @Test
    public void bspEndpointSkipDDPEventBloodReceived() throws Exception {
        String suffix = "_tt2";
        bspKitEndpointSkipDDPEventTriggered(2, "BLOOD_RECEIVED", suffix, INSTANCE_ID);
        //check if event_triggered = 1, which would mean it was sent
        Assert.assertNull(DBTestUtil.getQueryDetail("select * from EVENT_QUEUE queue, ddp_kit_request request where queue.DSM_KIT_REQUEST_ID = request.dsm_kit_request_id and request.ddp_label = ? AND queue.EVENT_TRIGGERED = 1", "FAKE_DSM_LABEL_UID" + suffix, "EVENT_ID"));
    }

    private void bspKitEndpointSkipDDPEventTriggered(int kitType, String eventName, String suffix, String ddpInstance) throws Exception {
        DBTestUtil.insertLatestKitRequest(cfg.getString("portal.insertKitRequest"), cfg.getString("portal.insertKit"),
                suffix, kitType, ddpInstance);
        DBTestUtil.executeQuery("insert into ddp_participant_event (event, ddp_participant_id, ddp_instance_id, date, done_by) values (\'" + eventName + "\', \'" + FAKE_DDP_PARTICIPANT_ID + suffix + "\', \'" + ddpInstance + "\', \'" + System.currentTimeMillis() + "\', \'1\')");
        DBTestUtil.setKitToSent("FAKE_SPK_UUID" + suffix, "FAKE_DSM_LABEL_UID" + suffix);
        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, "/api/Kits/FAKE_SPK_UUID" + suffix, testUtil.buildHeaders(cfg.getString("bsp.secret"))).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
    }

    @Test
    public void bspEndpointNoDDPEventSalivaReceived() throws Exception {
        String suffix = "_tt6";
        bspKitEndpointDDPEventTriggered(1, "SALIVA_RECEIVED", suffix, INSTANCE_ID_2);
        Assert.assertNull(DBTestUtil.getQueryDetail("select * from EVENT_QUEUE queue, ddp_kit_request request where queue.DSM_KIT_REQUEST_ID = request.dsm_kit_request_id and request.ddp_label = ?", "FAKE_DSM_LABEL_UID" + suffix, "EVENT_ID"));
    }

    @Test
    public void bspEndpointDDPEventSalivaReceived2() throws Exception {
        String suffix = "_tt1";
        bspKitEndpointDDPEventTriggered(1, "SALIVA_RECEIVED", suffix, INSTANCE_ID);
        Assert.assertNotNull(DBTestUtil.getQueryDetail("select * from EVENT_QUEUE queue, ddp_kit_request request where queue.DSM_KIT_REQUEST_ID = request.dsm_kit_request_id and request.ddp_label = ?", "FAKE_DSM_LABEL_UID" + suffix, "EVENT_ID"));
    }

    @Test
    public void bspEndpointDDPEventBloodReceived() throws Exception {
        String suffix = "_tt8";
        bspKitEndpointDDPEventTriggered(2, "BLOOD_RECEIVED", suffix, INSTANCE_ID);
        Assert.assertNotNull(DBTestUtil.getQueryDetail("select * from EVENT_QUEUE queue, ddp_kit_request request where queue.DSM_KIT_REQUEST_ID = request.dsm_kit_request_id and request.ddp_label = ?", "FAKE_DSM_LABEL_UID" + suffix, "EVENT_ID"));
    }

    private void bspKitEndpointDDPEventTriggered(int kitType, String eventName, String suffix, String ddpInstance) throws Exception {
        mockDDP.when(
                request().withPath("/ddp/participantevent/FAKE_DDP_PARTICIPANT_ID" + suffix))
                .respond(response().withStatusCode(200));
        DBTestUtil.insertLatestKitRequest(cfg.getString("portal.insertKitRequest"), cfg.getString("portal.insertKit"),
                suffix, kitType, ddpInstance);
        DBTestUtil.setKitToSent("FAKE_SPK_UUID" + suffix, "FAKE_DSM_LABEL_UID" + suffix);
        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, "/api/Kits/FAKE_SPK_UUID" + suffix, testUtil.buildHeaders(cfg.getString("bsp.secret"))).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
    }

    @Test
    public void bspEndpointSaliva() throws Exception {
        bspEndpoint(INSTANCE_ID, 1, "_test", "Saliva", "Oragene Kit", "SC-123");
    }

    @Test
    public void bspEndpointBlood() throws Exception {
        bspEndpoint(INSTANCE_ID, 2, "_test2", "Whole Blood:Streck Cell-Free Preserved", "Vacutainer Cell-Free DNA Tube Camo-Top [10mL]", "SC-123");
    }

    private void bspEndpoint(String instanceId, int kitType, String suffix, String materialInfo, String receptacle, String collection) throws Exception {
        mockDDP.when(
                request().withPath("/ddp/participantevent/FAKE_DDP_PARTICIPANT_ID" + suffix))
                .respond(response().withStatusCode(200));
        DBTestUtil.insertLatestKitRequest(cfg.getString("portal.insertKitRequest"), cfg.getString("portal.insertKit"),
                suffix, kitType, instanceId);
        DBTestUtil.setKitToSent("FAKE_SPK_UUID" + suffix, "FAKE_DSM_LABEL_UID" + suffix);

        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, "/api/Kits/FAKE_SPK_UUID" + suffix, testUtil.buildHeaders(cfg.getString("bsp.secret"))).returnResponse();

        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        Assert.assertNotNull(DDPRequestUtil.getContentAsString(response));

        Gson gson = new GsonBuilder().create();
        KitInfo bspKitInfo = gson.fromJson(DDPRequestUtil.getContentAsString(response), KitInfo.class);

        Assert.assertEquals(1, bspKitInfo.getOrganismClassificationId()); //human
        Assert.assertEquals("FAKE_BSP_COLL_ID" + suffix, bspKitInfo.getCollaboratorParticipantId());
        Assert.assertEquals("FAKE_BSP_SAM_ID" + suffix, bspKitInfo.getCollaboratorSampleId());
        Assert.assertEquals("U", bspKitInfo.getGender());
        Assert.assertEquals(materialInfo, bspKitInfo.getMaterialInfo());
        Assert.assertEquals(receptacle, bspKitInfo.getReceptacleName());
        Assert.assertEquals(collection, bspKitInfo.getSampleCollectionBarcode());

        //check that bsp scan added received_date to kit
        Assert.assertNotNull(DBTestUtil.getQueryDetail("select * from ddp_kit where kit_label = ?", "FAKE_SPK_UUID" + suffix, "receive_date"));

        //check that bsp scan triggered ddp event
        Assert.assertNotNull(DBTestUtil.getQueryDetail("select * from EVENT_QUEUE queue, ddp_kit_request request where queue.DSM_KIT_REQUEST_ID = request.dsm_kit_request_id and request.ddp_label = ?", "FAKE_DSM_LABEL_UID" + suffix, "EVENT_ID"));
    }

    @Test
    public void bspExitedKitWithNotification() throws Exception {
        HttpResponse response = bspKit("exitedK2");
        Gson gson = new GsonBuilder().create();
        BSPKitStatus bspStatus = gson.fromJson(DDPRequestUtil.getContentAsString(response), BSPKitStatus.class);
        Assert.assertEquals("EXITED", bspStatus.getStatus());

        String emailId = DBTestUtil.getQueryDetail("select * from EMAIL_QUEUE where EMAIL_RECORD_ID = ? and REMINDER_TYPE=\"NA\" and EMAIL_DATE_PROCESSED is null limit 1", "EXITED_KIT_RECEIVED_NOTIFICATION", "EMAIL_ID");
        Assert.assertNotNull(emailId);

        //delete kit so that other exit kit is not failing
        DBTestUtil.removedUnsentEmails();
    }

    @Test
    public void bspKitEndpointWithKitAlreadyReceived() throws Exception {
        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, "/api/Kits/testing123", testUtil.buildHeaders(cfg.getString("bsp.secret"))).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        //check that no email is triggered, because it is already received
        Assert.assertNull(DBTestUtil.getQueryDetail("select * from EVENT_QUEUE queue, ddp_kit_request request where queue.DSM_KIT_REQUEST_ID = request.dsm_kit_request_id and request.ddp_label = ?", "XZGIAKSJELUA4EDEF0KN", "EVENT_ID"));
    }

    @Test
    public void refundKitOnExit() throws Exception {
        DBTestUtil.insertLatestKitRequest(cfg.getString("portal.insertKitRequest"), cfg.getString("portal.insertKit"),
                "_refundKit_express", 1, INSTANCE_ID, FAKE_DDP_PARTICIPANT_ID + "_refundKit");

        DBTestUtil.insertLatestKitRequest(cfg.getString("portal.insertKitRequest"), cfg.getString("portal.insertKit"),
                "_refundKit_normal", 2, INSTANCE_ID, FAKE_DDP_PARTICIPANT_ID + "_refundKit");

        //create normal label
        triggerLabelCreationAndWaitForLabel(TEST_DDP, null, 30);

        ArrayList strings = new ArrayList<>();
        strings.add(FAKE_LATEST_KIT + "_refundKit_express");
        strings.add(FAKE_DDP_PARTICIPANT_ID + "_refundKit");
        String kitRequestId1 = DBTestUtil.getStringFromQuery(SELECT_KITREQUEST_QUERY, strings, "dsm_kit_request_id");
        strings = new ArrayList<>();
        strings.add(FAKE_LATEST_KIT + "_refundKit_normal");
        strings.add(FAKE_DDP_PARTICIPANT_ID + "_refundKit");
        String kitRequestId2 = DBTestUtil.getStringFromQuery(SELECT_KITREQUEST_QUERY, strings, "dsm_kit_request_id");
        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, "/ui/" + "expressKit/" + kitRequestId1 + "?userId=1", testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        mockDDP.when(
                request().withPath("/ddp/exitparticipantrequest/" + FAKE_DDP_PARTICIPANT_ID + "_refundKit"))
                .respond(response().withStatusCode(200));
        RouteTest.exitPat(FAKE_DDP_PARTICIPANT_ID + "_refundKit");

        //kit will have an error, because test label is not possible to refund
        //express kit
        String testValue = DBTestUtil.getQueryDetail("SELECT * from ddp_kit where dsm_kit_request_id = ? order by dsm_kit_id desc limit 1 ", kitRequestId1, "message");
        Assert.assertNotNull(testValue);
        Assert.assertEquals("To: Refund was not possible Return: Refund was not possible", testValue);
        //normal kit
        testValue = DBTestUtil.getQueryDetail("SELECT * from ddp_kit where dsm_kit_request_id = ? order by dsm_kit_id desc limit 1 ", kitRequestId2, "message");
        Assert.assertNotNull(testValue);
        Assert.assertEquals("To: Refund was not possible Return: Refund was not possible", testValue);
    }

    @Test
    public void sampleDiscard() throws Exception {
        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, "/ui/" + "discardSamples" + "?userId=1&realm=" + TEST_DDP, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        String message = DDPRequestUtil.getContentAsString(response);
        Gson gson = new GsonBuilder().create();
        KitDiscard[] kitDiscardsBefore = gson.fromJson(message, KitDiscard[].class);

        DBTestUtil.insertLatestKitRequest(cfg.getString("portal.insertKitRequest"), cfg.getString("portal.insertKit"),
                "_exitKit_1", 1, INSTANCE_ID, FAKE_DDP_PARTICIPANT_ID + "_exitKit");
        DBTestUtil.setKitToSent("FAKE_KIT_exitKit_1", "FAKE_DSM_LABEL_UID" + "_exitKit_1", System.currentTimeMillis());

        DBTestUtil.insertLatestKitRequest(cfg.getString("portal.insertKitRequest"), cfg.getString("portal.insertKit"),
                "_exitKit_2", 2, INSTANCE_ID, FAKE_DDP_PARTICIPANT_ID + "_exitKit");
        DBTestUtil.setKitToSent("FAKE_KIT_exitKit_2", "FAKE_DSM_LABEL_UID" + "_exitKit_2", System.currentTimeMillis());

        mockDDP.when(
                request().withPath("/ddp/exitparticipantrequest/" + FAKE_DDP_PARTICIPANT_ID + "_exitKit"))
                .respond(response().withStatusCode(200));
        RouteTest.exitPat(FAKE_DDP_PARTICIPANT_ID + "_exitKit");

        response = TestUtil.performGet(DSM_BASE_URL, "/ui/" + "discardSamples" + "?userId=1&realm=" + TEST_DDP, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        message = DDPRequestUtil.getContentAsString(response);
        KitDiscard[] kitDiscardsAfter = gson.fromJson(message, KitDiscard[].class);

        //check that the kits are now in the discard list
        Assert.assertTrue(kitDiscardsAfter.length > kitDiscardsBefore.length);
        Assert.assertTrue(kitDiscardsBefore.length + 2 == kitDiscardsAfter.length);

        //check that the kits are set to hold per default
        Assert.assertEquals("hold", DBTestUtil.getQueryDetail("select * from ddp_kit_discard dis, ddp_kit kit where dis.dsm_kit_request_id = kit.dsm_kit_request_id and kit.kit_label = ?", "FAKE_KIT_exitKit_1", "action"));
        Assert.assertEquals("hold", DBTestUtil.getQueryDetail("select * from ddp_kit_discard dis, ddp_kit kit where dis.dsm_kit_request_id = kit.dsm_kit_request_id and kit.kit_label = ?", "FAKE_KIT_exitKit_2", "action"));
    }

    @Test
    public void checkBloodKitIsInTrackingTable() throws Exception {
        DBTestUtil.insertLatestKitRequest(cfg.getString("portal.insertKitRequest"), cfg.getString("portal.insertKit"),
                "_BLOOD_TRACKING", 2, INSTANCE_ID);
        //check kit which is just 3 weeks old is in event table
        List<String> strings = new ArrayList<>();
        strings.add(FAKE_LATEST_KIT + "_BLOOD_TRACKING");
        strings.add(FAKE_DDP_PARTICIPANT_ID + "_BLOOD_TRACKING");
        String kitId = DBTestUtil.getStringFromQuery(RouteTest.SELECT_KITREQUEST_QUERY, strings, "dsm_kit_request_id");

        //use endpoint to set kit to sent
        String json = "[{\"leftValue\":\"FAKE_MF_" + kitId + "\", \"rightValue\": \"" + FAKE_DSM_LABEL_UID + "_BLOOD_TRACKING" + "\"}]";
        HttpResponse response = TestUtil.perform(Request.Post(DSM_BASE_URL + "/ui/" + "finalScan?userId=26"), json, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        String message = DDPRequestUtil.getContentAsString(response);
        KitStatusChangeRoute.ScanError[] scanErrors = new GsonBuilder().create().fromJson(message, KitStatusChangeRoute.ScanError[].class);
        //check that kit came back as scan error
        Assert.assertTrue(scanErrors.length > 0);

        //use endpoint to give kit tracking number
        json = "[{\"leftValue\":\"FAKE_TRACKING_" + kitId + "\", \"rightValue\": \"" + "FAKE_MF_" + kitId + "\"}]";
        response = TestUtil.perform(Request.Post(DSM_BASE_URL + "/ui/" + "trackingScan?userId=26"), json, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        //use endpoint to set kit to sent
        json = "[{\"leftValue\":\"FAKE_MF_" + kitId + "\", \"rightValue\": \"" + FAKE_DSM_LABEL_UID + "_BLOOD_TRACKING" + "\"}]";
        response = TestUtil.perform(Request.Post(DSM_BASE_URL + "/ui/" + "finalScan?userId=26"), json, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        message = DDPRequestUtil.getContentAsString(response);
        scanErrors = new GsonBuilder().create().fromJson(message, KitStatusChangeRoute.ScanError[].class);
        //check that kit came NOT back as scan error
        Assert.assertTrue(scanErrors.length == 0);
    }

    @Test
    public void triggerBloodSentEmail() throws Exception {
        triggerBloodSentEmail("_BLOOD_SENT");
    }

    private String triggerBloodSentEmail(String suffix) throws Exception {

        DBTestUtil.insertLatestKitRequest(cfg.getString("portal.insertKitRequest"), cfg.getString("portal.insertKit"),
                suffix, 2, INSTANCE_ID);

        //get kit ID
        List<String> strings = new ArrayList<>();
        strings.add(FAKE_LATEST_KIT + suffix);
        strings.add(FAKE_DDP_PARTICIPANT_ID + suffix);
        String kitId = DBTestUtil.getStringFromQuery(RouteTest.SELECT_KITREQUEST_QUERY, strings, "dsm_kit_request_id");

        //use endpoint to give kit tracking number
        String json = "[{\"leftValue\":\"FAKE_TRACKING_" + kitId + "\", \"rightValue\": \"" + "FAKE_MF_" + kitId + "\"}]";
        HttpResponse response = TestUtil.perform(Request.Post(DSM_BASE_URL + "/ui/" + "trackingScan?userId=26"), json, testUtil.buildAuthHeaders()).returnResponse();

        //use endpoint to set kit to sent
        json = "[{\"leftValue\":\"FAKE_MF_" + kitId + "\", \"rightValue\": \"" + FAKE_DSM_LABEL_UID + suffix + "\"}]";
        response = TestUtil.perform(Request.Post(DSM_BASE_URL + "/ui/" + "finalScan?userId=26"), json, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        String message = DDPRequestUtil.getContentAsString(response);
        KitStatusChangeRoute.ScanError[] scanErrors = new GsonBuilder().create().fromJson(message, KitStatusChangeRoute.ScanError[].class);
        //check that kit came NOT back as scan error
        Assert.assertTrue(scanErrors.length == 0);

        //check kit sent is in event queue
        Assert.assertNotNull(DBTestUtil.getQueryDetail("select * from EVENT_QUEUE WHERE DSM_KIT_REQUEST_ID = ?", kitId, "EVENT_ID"));
        return kitId;
    }

    @Test
    public void noSalivaSentTrigger() throws Exception {
        DBTestUtil.insertLatestKitRequest(cfg.getString("portal.insertKitRequest"), cfg.getString("portal.insertKit"),
                "_SALIVA_SENT", 1, INSTANCE_ID);

        //check kit which is just 3 weeks old is in event table
        List<String> strings = new ArrayList<>();
        strings.add(FAKE_LATEST_KIT + "_SALIVA_SENT");
        strings.add(FAKE_DDP_PARTICIPANT_ID + "_SALIVA_SENT");
        String kitId = DBTestUtil.getStringFromQuery(RouteTest.SELECT_KITREQUEST_QUERY, strings, "dsm_kit_request_id");

        //use endpoint to set kit to sent
        String json = "[{\"leftValue\":\"FAKE_MF_" + kitId + "\", \"rightValue\": \"" + FAKE_DSM_LABEL_UID + "_SALIVA_SENT" + "\"}]";
        HttpResponse response = TestUtil.perform(Request.Post(DSM_BASE_URL + "/ui/" + "finalScan?userId=26"), json, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        String message = DDPRequestUtil.getContentAsString(response);
        KitStatusChangeRoute.ScanError[] scanErrors = new GsonBuilder().create().fromJson(message, KitStatusChangeRoute.ScanError[].class);
        //check that kit came NOT back as scan error
        Assert.assertTrue(scanErrors.length == 0);

        //check kit sent is in event queue
        Assert.assertNull(DBTestUtil.getQueryDetail("select * from EVENT_QUEUE WHERE DSM_KIT_REQUEST_ID = ?", kitId, "EVENT_ID"));
    }

    @Test //test to check that reminder is not sent multiple times
    public void triggerReminder() throws Exception {
        String suffix = "_SENT_REMINDER_TEST";
        String kitId = triggerBloodSentEmail(suffix);
        DBTestUtil.setKitToSent("FAKE_MF_" + kitId, FAKE_DSM_LABEL_UID + suffix, System.currentTimeMillis() - (3 * DBTestUtil.WEEK));

        eventUtil.triggerReminder();
        //check kit which is just 3 weeks old is in event table
        List<String> strings = new ArrayList<>();
        strings.add(FAKE_LATEST_KIT + suffix);
        strings.add(FAKE_DDP_PARTICIPANT_ID + suffix);
        kitId = DBTestUtil.getStringFromQuery(RouteTest.SELECT_KITREQUEST_QUERY, strings, "dsm_kit_request_id");
        //check that kit is twice in table because of sent and reminder
        Assert.assertEquals("2", DBTestUtil.getQueryDetail("select count(dsm_kit_request_id) from EVENT_QUEUE WHERE DSM_KIT_REQUEST_ID = ? AND EVENT_TRIGGERED = 1", kitId, "count(dsm_kit_request_id)"));

        eventUtil.triggerReminder();
        //check kit which is just 3 weeks old is in event table
        strings = new ArrayList<>();
        strings.add(FAKE_LATEST_KIT + suffix);
        strings.add(FAKE_DDP_PARTICIPANT_ID + suffix);
        kitId = DBTestUtil.getStringFromQuery(RouteTest.SELECT_KITREQUEST_QUERY, strings, "dsm_kit_request_id");
        //check that kit is ONLY twice in table because of sent and reminder and was not added again!
        Assert.assertEquals("2", DBTestUtil.getQueryDetail("select count(dsm_kit_request_id) from EVENT_QUEUE WHERE DSM_KIT_REQUEST_ID = ? AND EVENT_TRIGGERED = 1", kitId, "count(dsm_kit_request_id)"));
    }

    @Test
    public void uploadKitWriteConsentIntoBucket() throws Exception {
        File file = TestUtil.getResouresFile("Consent.pdf");
        byte[] bytes = Files.readAllBytes(Paths.get(file.getPath()));
        //setting up mock angio
        mockDDP.when(
                request().withPath("/ddp/participants/1771332079.c31d1fb7-82fd-4a86-81d5-6d01b846a8f7/consentpdf"))
                .respond(response().withStatusCode(200).withBody(bytes));
        mockDDP.when(
                request().withPath("/ddp/participants/-2084982510.fe0f352f-3474-45ac-b0a9-974e24092b06/consentpdf"))
                .respond(response().withStatusCode(200).withBody(bytes));
        mockDDP.when(
                request().withPath("/ddp/participants/1_3/consentpdf"))
                .respond(response().withStatusCode(200).withBody(bytes));
        mockDDP.when(
                request().withPath("/ddp/participants/7/consentpdf"))
                .respond(response().withStatusCode(200).withBody(bytes));
        mockDDP.when(
                request().withPath("/ddp/participants/1771332079.c31d1fb7-82fd-4a86-81d5-6d01b846a8f7/releasepdf"))
                .respond(response().withStatusCode(200).withBody(bytes));
        mockDDP.when(
                request().withPath("/ddp/participants/-2084982510.fe0f352f-3474-45ac-b0a9-974e24092b06/releasepdf"))
                .respond(response().withStatusCode(200).withBody(bytes));
        mockDDP.when(
                request().withPath("/ddp/participants/1_3/releasepdf"))
                .respond(response().withStatusCode(200).withBody(bytes));
        mockDDP.when(
                request().withPath("/ddp/participants/7/releasepdf"))
                .respond(response().withStatusCode(200).withBody(bytes));

        String roleId = DBTestUtil.getQueryDetail("SELECT * from instance_role where name = ?", "pdf_download_consent", "instance_role_id");
        String secondRoleId = DBTestUtil.getQueryDetail("SELECT * from instance_role where name = ?", "pdf_download_release", "instance_role_id");
        try {
            DBTestUtil.executeQuery("INSERT INTO ddp_instance_role SET ddp_instance_id = " + INSTANCE_ID + ", instance_role_id = " + roleId);
            DBTestUtil.executeQuery("INSERT INTO ddp_instance_role SET ddp_instance_id = " + INSTANCE_ID + ", instance_role_id = " + secondRoleId);
            String kitType = "TEST";
            //upload kits for one type
            String csvContent = TestUtil.readFile("KitUploadTestDDP.txt");
            HttpResponse response = TestUtil.perform(Request.Post(DSM_BASE_URL + "/ui/" + "kitUpload?realm=" + TEST_DDP + "&kitType=SALIVA&userId=26"), csvContent, testUtil.buildAuthHeaders()).returnResponse();
            Assert.assertEquals(200, response.getStatusLine().getStatusCode());
            PDFAudit pdfAudit = new PDFAudit();
            pdfAudit.checkAndSavePDF();
            checkFileInBucket("1771332079.c31d1fb7-82fd-4a86-81d5-6d01b846a8f7/readonly/1771332079.c31d1fb7-82fd-4a86-81d5-6d01b846a8f7_consent", TEST_DDP.toLowerCase());
            checkFileInBucket("1771332079.c31d1fb7-82fd-4a86-81d5-6d01b846a8f7/readonly/1771332079.c31d1fb7-82fd-4a86-81d5-6d01b846a8f7_release", TEST_DDP.toLowerCase());
            checkFileInBucket("-2084982510.fe0f352f-3474-45ac-b0a9-974e24092b06/readonly/-2084982510.fe0f352f-3474-45ac-b0a9-974e24092b06_consent", TEST_DDP.toLowerCase());
            checkFileInBucket("-2084982510.fe0f352f-3474-45ac-b0a9-974e24092b06/readonly/-2084982510.fe0f352f-3474-45ac-b0a9-974e24092b06_release", TEST_DDP.toLowerCase());
            checkFileInBucket("1_3/readonly/1_3_consent", TEST_DDP.toLowerCase());
            checkFileInBucket("1_3/readonly/1_3_release", TEST_DDP.toLowerCase());
            checkFileInBucket("7/readonly/7_consent", TEST_DDP.toLowerCase());
            checkFileInBucket("7/readonly/7_release", TEST_DDP.toLowerCase());
            checkBucket(TEST_DDP.toLowerCase());
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

    public static void checkFileInBucket(String fileName, String instanceName) throws Exception {
        String projectName = cfg.getString("portal.googleProjectName");
        String bucketName = projectName + "_dsm_" + instanceName;

        List<String> fileNames = GoogleBucket.getFiles(cfg.getString("portal.googleProjectCredentials"), bucketName, bucketName);
        boolean found = false;
        for (String name : fileNames) {
            if (name.startsWith(fileName)) {
                found = true;
                break;
            }
        }
        Assert.assertTrue(found);
    }

    public static void checkBucket(String instanceName) throws Exception {
        String projectName = cfg.getString("portal.googleProjectName");
        String bucketName = projectName + "_dsm_" + instanceName;

        List<String> fileNames = GoogleBucket.getFiles(cfg.getString("portal.googleProjectCredentials"), bucketName, bucketName);

        for (String fileName : fileNames) {
            byte[] downloadedFile = GoogleBucket.downloadFile(cfg.getString("portal.googleProjectCredentials"), cfg.getString("portal.googleProjectName"),
                    bucketName, fileName);
            Assert.assertNotNull(downloadedFile);

            //delete file
            boolean fileDeleted = GoogleBucket.deleteFile(cfg.getString("portal.googleProjectCredentials"), cfg.getString("portal.googleProjectName"),
                    bucketName, fileName);
            Assert.assertTrue(fileDeleted);
        }
    }

    public static boolean bucketExists(String instanceName) throws Exception {
        String projectName = cfg.getString("portal.googleProjectName");
        String bucketName = projectName + "_dsm_" + instanceName;
        return GoogleBucket.bucketExists(cfg.getString("portal.googleProjectCredentials"), cfg.getString("portal.googleProjectName"), bucketName);
    }

    @Test
    @Ignore ("We won't be using external shipper... for now...")
    public void promiseFileUpload() throws Exception {
        DBTestUtil.deleteAllKitData("00004");
        DBTestUtil.deleteAllKitData("00003");

        String gbfResponse = TestUtil.readFile("gbf/OrderResponse.json");
        mockDDP.when(
                request().withPath("/order"))
                .respond(response().withStatusCode(200).withBody(gbfResponse));

        //upload kits for one type
        String csvContent = TestUtil.readFile("KitUploadPromise.txt");
        HttpResponse response = TestUtil.perform(Request.Post(DSM_BASE_URL + "/ui/" + "kitUpload?realm=" + DDP_PROMISE + "&kitType=SUB_KITS&userId=1"), csvContent, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        response = TestUtil.performGet(DSM_BASE_URL, "/ui/" + "kitRequests?realm=" + DDP_PROMISE + "&kitType=BLOOD&target=uploaded", testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        String message = DDPRequestUtil.getContentAsString(response);
        Gson gson = new GsonBuilder().create();
        KitRequestShipping[] kits = gson.fromJson(message, KitRequestShipping[].class);
        List<String> strings = new ArrayList<>();
        strings.add("promise");
        String kitsBeforeUpload = DBTestUtil.getStringFromQuery("select count(*) from ddp_kit_request req, ddp_kit kit where req.dsm_kit_request_id = kit.dsm_kit_request_id AND NOT kit.kit_complete <=> 1 and ddp_instance_id = (select ddp_instance_id from ddp_instance where instance_name = ?) and kit_type_id = 2", strings, "count(*)");
        Assert.assertTrue(kits.length > 0);
        Assert.assertEquals(Integer.parseInt(kitsBeforeUpload), kits.length);

        response = TestUtil.performGet(DSM_BASE_URL, "/ui/" + "kitRequests?realm=" + DDP_PROMISE + "&kitType=SUB_KITS&target=uploaded", testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        message = DDPRequestUtil.getContentAsString(response);
        gson = new GsonBuilder().create();
        kits = gson.fromJson(message, KitRequestShipping[].class);
        kitsBeforeUpload = DBTestUtil.getStringFromQuery("select count(*) from ddp_kit_request req, ddp_kit kit where req.dsm_kit_request_id = kit.dsm_kit_request_id AND NOT kit.kit_complete <=> 1 and ddp_instance_id = (select ddp_instance_id from ddp_instance where instance_name = ?) and kit_type_id = 2", strings, "count(*)");
        String noReturnKits = DBTestUtil.getStringFromQuery("select count(*) from ddp_kit_request req, ddp_kit kit where req.dsm_kit_request_id = kit.dsm_kit_request_id AND NOT kit.kit_complete <=> 1 and ddp_instance_id = (select ddp_instance_id from ddp_instance where instance_name = ?) and kit_type_id = 8", strings, "count(*)");
        Assert.assertEquals(Integer.parseInt(kitsBeforeUpload) + Integer.parseInt(noReturnKits), kits.length);

        EasyPostUtil easyPostUtil = new EasyPostUtil(DDP_PROMISE);
        for (KitRequest kit : kits) {
            if (kit.getParticipantId().equals("00004") || kit.getParticipantId().equals("00003")) {
                Assert.assertTrue(kit.getExternalOrderNumber().equals("ORD3343") || kit.getExternalOrderNumber().equals("ORD3344"));
                Assert.assertEquals("6177146666", easyPostUtil.getAddress(((KitRequestShipping) kit).getEasypostAddressId()).getPhone());
            }
        }
    }

    @Test
    @Ignore ("We won't be using external shipper... for now...")
    public void retryGBFOrder() throws Exception {
        mockDDP.when(
                request().withPath("/order"))
                .respond(response().withStatusCode(500));
        String csvContent = TestUtil.readFile("KitUploadTestDDP2.txt");
        HttpResponse response = TestUtil.perform(Request.Post(DSM_BASE_URL + "/ui/" + "kitUpload?realm=" + DDP_PROMISE + "&kitType=SUB_KITS&userId=1"), csvContent, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(500, response.getStatusLine().getStatusCode());
    }

    @Test
    public void collaboratorIdsAtUpload() throws Exception {
        //remove kits in case other test method was first...
        DBTestUtil.deleteAllKitData("1112321.22-698-965-659-666");
        DBTestUtil.deleteAllKitData("1112321.22-698-965-659-668");
        DBTestUtil.deleteAllKitData("DLF90348FK65DIR88");
        DBTestUtil.deleteAllParticipantData("1112321.22-698-965-659-668", true);

        //upload kits for pt not already in DSM
        String csvContent = TestUtil.readFile("KitUploadMigratedDDP.txt");
        HttpResponse response = TestUtil.perform(Request.Post(DSM_BASE_URL + "/ui/" + "kitUpload?realm=" + TEST_DDP_MIGRATED + "&kitType=SALIVA&userId=26"), csvContent, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        //insert a kit for pt of migrated ddp (will be uploaded with legacy shortId)
        DBTestUtil.insertLatestKitRequest(cfg.getString("portal.insertKitRequest"), cfg.getString("portal.insertKit"), "M1", 1, INSTANCE_ID_MIGRATED,
                "adr_6c3ace20442b49bd8fae9a661e481c9e", "shp_f470591c3fb441a68dbb9b76ecf3bb3d", "1112321.22-698-965-659-666",0);
        //change bsp_collaborator_ids
        DBTestUtil.executeQuery("UPDATE ddp_kit_request set bsp_collaborator_participant_id = \"MigratedProject_0011\", bsp_collaborator_sample_id =\"MigratedProject_0011_SALIVA\" where ddp_participant_id = \"1112321.22-698-965-659-666\"");

        //insert a kit for pt of migrated ddp (will be uploaded with pepper HRUID)
        DBTestUtil.insertLatestKitRequest(cfg.getString("portal.insertKitRequest"), cfg.getString("portal.insertKit"), "M2", 1, INSTANCE_ID_MIGRATED,
                "adr_6c3ace20442b49bd8fae9a661e481c9e", "shp_f470591c3fb441a68dbb9b76ecf3bb3d", "1112321.22-698-965-659-667", 0);
        //change bsp_collaborator_ids
        DBTestUtil.executeQuery("UPDATE ddp_kit_request set bsp_collaborator_participant_id = \"MigratedProject_0012\", bsp_collaborator_sample_id =\"MigratedProject_0012_SALIVA\" where ddp_participant_id = \"1112321.22-698-965-659-667\"");

        //upload duplicates
        Map<String, KitType> kitTypes = KitType.getKitLookup();
        String key = "SALIVA_" + INSTANCE_ID_MIGRATED;
        KitType kitType = kitTypes.get(key);

        String dupParticipants = TestUtil.readFile("KitUploadMigratedDDP.json");
        response = TestUtil.perform(Request.Post(DSM_BASE_URL + "/ui/" + "kitUpload?realm=" + TEST_DDP_MIGRATED + "&kitType=SALIVA&userId=26&uploadAnyway=true"), dupParticipants, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        List<String> strings = new ArrayList<>();
        strings.add(TEST_DDP_MIGRATED);
        strings.add(String.valueOf(kitType.getKitTypeId()));
        strings.add("1112321.22-698-965-659-666");
        String uploadedCorrectShortId = DBTestUtil.getStringFromQuery(KIT_QUERY, strings, "bsp_collaborator_sample_id");
        //Gen2 with sample in dsm but uploaded with gen2 id -> should still have gen2 collaborator id
        Assert.assertEquals("MigratedProject_0011_SALIVA_2", uploadedCorrectShortId);

        strings = new ArrayList<>();
        strings.add(TEST_DDP_MIGRATED);
        strings.add(String.valueOf(kitType.getKitTypeId()));
        strings.add("1112321.22-698-965-659-667");
        String uploadedWrongShortId = DBTestUtil.getStringFromQuery(KIT_QUERY, strings, "bsp_collaborator_sample_id");
        //Gen2 with sample in dsm but uploaded with pepper HRUID -> should get gen2 collaborator id
        Assert.assertEquals("MigratedProject_0012_SALIVA_2", uploadedWrongShortId);

        strings = new ArrayList<>();
        strings.add(TEST_DDP_MIGRATED);
        strings.add(String.valueOf(kitType.getKitTypeId()));
        strings.add("DLF90348FK65DIR88");
        String uploadedPepperPT = DBTestUtil.getStringFromQuery(KIT_QUERY, strings, "bsp_collaborator_sample_id");
        //Pepper participant should have pepper HRUID in collaborator id
        Assert.assertEquals("MigratedProject_PDKL99_SALIVA", uploadedPepperPT);

        strings = new ArrayList<>();
        strings.add(TEST_DDP_MIGRATED);
        strings.add(String.valueOf(kitType.getKitTypeId()));
        strings.add("1112321.22-698-965-659-668");
        String collabNull = DBTestUtil.getStringFromQuery(KIT_QUERY, strings, "bsp_collaborator_sample_id");
        //Gen2 but no sample in DSM so should ask Pepper for shortId when label gets created
        Assert.assertEquals(null, collabNull);

        //setup the response to get the shortID from the ddp
        String messageParticipant = TestUtil.readFile("ddpResponses/ParticipantsMigrated.json");
        mockDDP.when(
                request().withPath("/dsm/studies/migratedDDP/ddp/participants/1112321.22-698-965-659-668"))
                .respond(response().withStatusCode(200).withBody(messageParticipant));

        //create label an therefore collaborator id
        triggerLabelCreationAndWaitForLabel(TEST_DDP_MIGRATED, "SALIVA", 60);
        String collabSet = DBTestUtil.getStringFromQuery(KIT_QUERY, strings, "bsp_collaborator_sample_id");
        //Gen2 but no sample in DSM so should ask Pepper for shortId when label gets created
        Assert.assertNotEquals(collabNull, collabSet);
        Assert.assertEquals("MigratedProject_PLKP09_SALIVA", collabSet);
    }

    @Test
    public void collaboratorIdsOnlyTissueExists() throws Exception {
        //remove kits in case other test method was first...
        DBTestUtil.deleteAllKitData("1112321.22-698-965-659-668");
        DBTestUtil.deleteAllKitData("DLF90348FK65DIR88");

        //add tissue collaborator sample id for 1112321.22-698-965-659-668
        DBTestUtil.createTestData(TEST_DDP_MIGRATED, "1112321.22-698-965-659-668", "FAKE_DDP_PHYSICIAN_ID");
        String participantId = DBTestUtil.getParticipantIdOfTestParticipant("1112321.22-698-965-659-668");
        String oncHistoryId = addOncHistoryDetails(participantId);
        String tissueId = RouteTest.addTissue(oncHistoryId);
        RouteTest.changeTissueValue(tissueId, oncHistoryId, "t.collaboratorSampleId", "MigratedProject_0014_T1", "collaborator_sample_id");

        //upload kits for pt not already in DSM
        String csvContent = TestUtil.readFile("KitUploadMigratedDDP.txt");
        HttpResponse response = TestUtil.perform(Request.Post(DSM_BASE_URL + "/ui/" + "kitUpload?realm=" + TEST_DDP_MIGRATED + "&kitType=SALIVA&userId=26"), csvContent, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        //check collaborator sample id
        Map<String, KitType> kitTypes = KitType.getKitLookup();
        String key = "SALIVA_" + INSTANCE_ID_MIGRATED;
        KitType kitType = kitTypes.get(key);

        List<String> strings = new ArrayList<>();
        strings.add(TEST_DDP_MIGRATED);
        strings.add(String.valueOf(kitType.getKitTypeId()));
        strings.add("1112321.22-698-965-659-668");
        String collabSet = DBTestUtil.getStringFromQuery(KIT_QUERY, strings, "bsp_collaborator_participant_id");
        //Gen2 with tissue sample in DSM so should get Gen2 legacyShortId when label gets created
        Assert.assertEquals("MigratedProject_0014", collabSet);
        collabSet = DBTestUtil.getStringFromQuery(KIT_QUERY, strings, "bsp_collaborator_sample_id");
        //Gen2 with tissue sample in DSM so should get Gen2 legacyShortId when label gets created
        Assert.assertEquals("MigratedProject_0014_SALIVA", collabSet);
    }

    @Test
    public void kitRegistered() throws Exception {
        HttpResponse response = TestUtil.performGet(DSM_BASE_URL, "/api/" + "KitsRegistered?barcode=a&barcode=b&barcode=testing123", testUtil.buildHeaders(cfg.getString("bsp.secret"))).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        Gson gson = new GsonBuilder().create();
        BSPKitRegistration[] kitRegistrations = gson.fromJson(DDPRequestUtil.getContentAsString(response), BSPKitRegistration[].class);
        Assert.assertTrue(kitRegistrations.length == 3);
        for (BSPKitRegistration kit : kitRegistrations) {
            if (kit.getBarcode().equals("testing123")) {
                Assert.assertTrue(kit.isDsmKit());
            }
            else {
                Assert.assertFalse(kit.isDsmKit());
            }
        }
    }

    @Test
    public void kitEasypostStatus() {
        //add kit
        DBTestUtil.insertLatestKitRequest(cfg.getString("portal.insertKitRequest"), cfg.getString("portal.insertKit"),
                "_DELIVERED_STATUS", 1, INSTANCE_ID_MIGRATED);

        //check kit which is just 3 weeks old is in event table
        List<String> strings = new ArrayList<>();
        strings.add(FAKE_LATEST_KIT + "_DELIVERED_STATUS");
        strings.add(FAKE_DDP_PARTICIPANT_ID + "_DELIVERED_STATUS");
        String kitId = DBTestUtil.getStringFromQuery(RouteTest.SELECT_KITREQUEST_QUERY, strings, "dsm_kit_request_id");

        //set kit to sent
        DBTestUtil.setKitToSent("FAKE_KIT_DELIVERED_STATUS", "FAKE_DSM_LABEL_UID_DELIVERED_STATUS", System.currentTimeMillis());

        //trigger delivered check
        KitUtil.getKitStatus();

        //check that delivered is now set
        Assert.assertNotNull(DBTestUtil.getQueryDetail("select * from ddp_kit where kit_label = ?", "FAKE_KIT_DELIVERED_STATUS", "easypost_shipment_date"));
    }

    @Test
    public void billingReference() throws Exception {
        //add kit
        String csvContent = "participantId\tsignature\tstreet1\tstreet2\tcity\tstate\tpostalCode\tcountry\n" +
                "BILLING_TEST\tBill Test\t320 Charles St\t\tCambridge\tMA\t02141\tUS";
        HttpResponse response = TestUtil.perform(Request.Post(DSM_BASE_URL + "/ui/" + "kitUpload?realm=" + TEST_DDP + "&kitType=SALIVA&userId=26"), csvContent, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        triggerLabelCreationAndWaitForLabel(TEST_DDP, "SALIVA", 20);

        //get shipping id
        String shippingId = DBTestUtil.getQueryDetail("select * from ddp_kit_request req, ddp_kit kit where req.dsm_kit_request_id = kit.dsm_kit_request_id and ddp_participant_id = ?", "BILLING_TEST", "easypost_to_id");

        EasyPostUtil easyPostUtil = new EasyPostUtil(TEST_DDP);
        Shipment shipment = easyPostUtil.getShipment(shippingId);
        Map<String, Object> options = shipment.getOptions();
        if (options != null) {
            Object billingReference = options.get("print_custom_1");
            if (billingReference != null) {
                Assert.assertEquals("CO Test", billingReference.toString());
            }
            else {
                Assert.fail("No billing reference");
            }
        }
        else {
            Assert.fail("No options");
        }
    }

    @Test
    public void noBillingReference() throws Exception {
        //add kit
        String csvContent = "participantId\tsignature\tstreet1\tstreet2\tcity\tstate\tpostalCode\tcountry\n" +
                "BILLING_TEST_2\tBill The Tester\t320 Charles St\t\tCambridge\tMA\t02141\tUS";
        HttpResponse response = TestUtil.perform(Request.Post(DSM_BASE_URL + "/ui/" + "kitUpload?realm=" + TEST_DDP_2 + "&kitType=SALIVA&userId=26"), csvContent, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        triggerLabelCreationAndWaitForLabel(TEST_DDP_2, "SALIVA", 20);

        //get shipping id
        String shippingId = DBTestUtil.getQueryDetail("select * from ddp_kit_request req, ddp_kit kit where req.dsm_kit_request_id = kit.dsm_kit_request_id and ddp_participant_id = ?", "BILLING_TEST_2", "easypost_to_id");

        EasyPostUtil easyPostUtil = new EasyPostUtil(TEST_DDP_2);
        Shipment shipment = easyPostUtil.getShipment(shippingId);
        Map<String, Object> options = shipment.getOptions();
        if (options != null) {
            Object billingReference = options.get("print_custom_1");
            Assert.assertNull(billingReference);
        }
        else {
            Assert.fail("No options");
        }
    }

    // TODO add tests
    // write test to see what happens if kit get reactivated but doesn't find previous deactivated kit
    // 1) checking that no label was created when uploading/requesting kits from ddp
    // 2) trigger label creation > trigger another label creation > check that not 2 labels got created
    // 3) status route for label creation job running
    // 4) trigger single or array label creation
    // 5) trigger label creation of all kits without label
}
