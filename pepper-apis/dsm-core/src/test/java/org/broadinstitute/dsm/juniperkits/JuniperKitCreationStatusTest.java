package org.broadinstitute.dsm.juniperkits;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.easypost.exception.EasyPostException;
import com.easypost.model.Address;
import com.easypost.model.Parcel;
import com.easypost.model.PostageLabel;
import com.easypost.model.Shipment;
import com.easypost.model.Tracker;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.DbTxnBaseTest;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.kit.KitCurrentStatus;
import org.broadinstitute.dsm.db.dto.kit.nonPepperKit.NonPepperKitStatusDto;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.kits.KitTestUtil;
import org.broadinstitute.dsm.model.kit.ScanResult;
import org.broadinstitute.dsm.model.nonpepperkit.JuniperKitRequest;
import org.broadinstitute.dsm.model.nonpepperkit.KitResponse;
import org.broadinstitute.dsm.model.nonpepperkit.NonPepperKitCreationService;
import org.broadinstitute.dsm.model.nonpepperkit.NonPepperStatusKitService;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.EasyPostUtil;
import org.broadinstitute.dsm.util.NotificationUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Tests the NonPepperKitCreationService and NonPepperStatusKitService class
 * To run this class use the following VM variables
 * -ea -Dconfig.file=[path to /pepper-apis/output-build-config/testing-inmemorydb.conf]
 */

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class JuniperKitCreationStatusTest extends DbTxnBaseTest {

    static final String instanceGuid = "Juniper-test-guid";
    static final String instanceName = "Juniper-test";
    static final String bspPrefix = "JuniperTestProject";
    static List<String> createdKitIds = new ArrayList<>();
    static DDPInstance ddpInstance;
    final String salivaKitType = "SALIVA";
    @Mock
    EasyPostUtil mockEasyPostUtil = mock(EasyPostUtil.class);
    @Mock
    Address mockEasyPostAddress = mock(Address.class);
    @Mock
    Shipment mockEasyPostShipment = mock(Shipment.class);
    @Mock
    Parcel mockEasyPostParcel = mock(Parcel.class);
    @Mock
    PostageLabel mockParticipantLabel = mock(PostageLabel.class);
    @Mock
    Tracker mockShipmentTracker = mock(Tracker.class);
    NonPepperKitCreationService nonPepperKitCreationService = new NonPepperKitCreationService();
    NonPepperStatusKitService nonPepperStatusKitService = new NonPepperStatusKitService();
    private static KitTestUtil juniperKitUtil;

    @BeforeClass
    public static void setupJuniperBefore() {
        juniperKitUtil =
                new KitTestUtil(instanceName, instanceGuid, "JuniperTestProject",  "Juniper-Group", "SALIVA", "SALIVA", null, true);
        juniperKitUtil.setupInstanceAndSettings();
        ddpInstance = DDPInstance.getDDPInstanceWithRoleByStudyGuid(instanceGuid, DBConstants.JUNIPER_STUDY_INSTANCE_ROLE);

    }

    @AfterClass
    public static void deleteJuniperInstance() {
        juniperKitUtil.deleteKitsArray(createdKitIds);
        juniperKitUtil.deleteGeneratedData();
    }

    @Before
    public void setUp() throws Exception {

        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testCurrentStatusField() {
        int rand = new Random().nextInt() & Integer.MAX_VALUE;
        JuniperKitRequest juniperTestKit = generateJuniperKitRequest(rand);
        createNonPepperTestKit(juniperTestKit);
        KitResponse kitResponse = nonPepperStatusKitService.getKitsBasedOnJuniperKitId(juniperTestKit.getJuniperKitId());
        verifyStatusKitResponse(kitResponse, juniperTestKit, rand, KitCurrentStatus.KIT_WITHOUT_LABEL.getValue());
        juniperKitUtil.changeKitToQueue(juniperTestKit.getJuniperParticipantID(), mockEasyPostUtil);
        kitResponse = nonPepperStatusKitService.getKitsBasedOnJuniperKitId(juniperTestKit.getJuniperKitId());
        verifyStatusKitResponse(kitResponse, juniperTestKit, rand, KitCurrentStatus.QUEUE.getValue());
        juniperTestKit.setDdpLabel(kitResponse.getKits().get(0).getDsmShippingLabel());
        List<ScanResult> scanResultList = juniperKitUtil.changeKitToSent(juniperTestKit.getDdpLabel(),
                "SOME_RANDOM_KIT_LABEL");
        Assert.assertFalse(
                scanResultList.stream().filter(scanError -> scanError.hasError()).findAny().isPresent());
        kitResponse = nonPepperStatusKitService.getKitsBasedOnJuniperKitId(juniperTestKit.getJuniperKitId());
        verifyStatusKitResponse(kitResponse, juniperTestKit, rand, KitCurrentStatus.SENT.getValue());
        NotificationUtil notificationUtil = mock(NotificationUtil.class);
        juniperKitUtil.changeKitToReceived(notificationUtil, "SOME_RANDOM_KIT_LABEL");
        kitResponse = nonPepperStatusKitService.getKitsBasedOnJuniperKitId(juniperTestKit.getJuniperKitId());
        verifyStatusKitResponse(kitResponse, juniperTestKit, rand, KitCurrentStatus.RECEIVED.getValue());

    }

    @Test
    public void testStatusByJuniperKitId() {
        int rand = new Random().nextInt() & Integer.MAX_VALUE;
        JuniperKitRequest juniperTestKit = generateJuniperKitRequest(rand);
        createNonPepperTestKit(juniperTestKit);
        KitResponse kitResponse = nonPepperStatusKitService.getKitsBasedOnJuniperKitId(juniperTestKit.getJuniperKitId());

        verifyStatusKitResponse(kitResponse, juniperTestKit, rand, KitCurrentStatus.KIT_WITHOUT_LABEL.getValue());
    }

    @Test
    public void testReturnOnlyKit() {
        int rand = new Random().nextInt() & Integer.MAX_VALUE;
        JuniperKitRequest juniperTestKit = generateJuniperKitRequest(rand);
        juniperTestKit.setReturnOnly(true);
        juniperTestKit.setKitLabel(juniperTestKit.getParticipantId() + ".kit");
        juniperTestKit.setReturnTrackingId(juniperTestKit.getJuniperParticipantID() + ".tracking");
        createNonPepperTestKit(juniperTestKit);
        KitResponse kitResponse = nonPepperStatusKitService.getKitsBasedOnJuniperKitId(juniperTestKit.getJuniperKitId());

        verifyStatusKitResponse(kitResponse, juniperTestKit, rand, KitCurrentStatus.SENT.getValue());
    }

    @Test
    public void testStatusByParticipantIdTest() {
        int rand = new Random().nextInt() & Integer.MAX_VALUE;
        JuniperKitRequest juniperTestKit = generateJuniperKitRequest(rand);
        createNonPepperTestKit(juniperTestKit);
        KitResponse kitResponse = nonPepperStatusKitService.getKitsBasedOnParticipantId(juniperTestKit.getJuniperParticipantID());
        verifyStatusKitResponse(kitResponse, juniperTestKit, rand, KitCurrentStatus.KIT_WITHOUT_LABEL.getValue());

    }
    /**
     * this method creates the juniperTestKitRequest in the database  by calling
     * `NonPepperKitCreationService.createNonPepperKit` and verifies the response is as expected
     * @param juniperTestKitRequest a JuniperKitRequest that can be passed to the kti creation service
     **/

    private void createNonPepperTestKit(JuniperKitRequest juniperTestKitRequest) {
        createdKitIds.add(juniperTestKitRequest.getJuniperKitId());
        when(mockEasyPostShipment.getPostageLabel()).thenReturn(mockParticipantLabel);
        when(mockEasyPostShipment.getTracker()).thenReturn(mockShipmentTracker);
        when(mockShipmentTracker.getPublicUrl()).thenReturn("PUBLIC_URL");
        when(mockParticipantLabel.getLabelUrl()).thenReturn("MOCK_LABEL_URL");
        when(mockEasyPostAddress.getId()).thenReturn("SOME_STRING");
        when(mockEasyPostUtil.getEasyPostAddressId(any(), any(), any())).thenReturn("SOME_STRING");
        try {
            when(mockEasyPostUtil.createParcel(any(), any(), any(), any())).thenReturn(mockEasyPostParcel);
            when(mockEasyPostUtil.getAddress(any())).thenReturn(mockEasyPostAddress);
            when(mockEasyPostUtil.createAddressWithoutValidation(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(
                    mockEasyPostAddress);
            when(mockEasyPostUtil.buyShipment(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(mockEasyPostShipment);
        } catch (EasyPostException e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
        List<KitRequestShipping> oldKits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", salivaKitType);
        KitResponse kitResponse =
                nonPepperKitCreationService.createNonPepperKit(juniperTestKitRequest, salivaKitType, mockEasyPostUtil, ddpInstance);
        Assert.assertFalse(kitResponse.isError());
        Assert.assertNotNull(kitResponse.getKits());
        Assert.assertEquals(1, kitResponse.getKits().size());
        NonPepperKitStatusDto kitStatus = kitResponse.getKits().get(0);
        Assert.assertEquals(juniperTestKitRequest.getJuniperKitId(), kitStatus.getJuniperKitId());
        Assert.assertNotNull(kitStatus.getDsmShippingLabel());
        Assert.assertNotNull(kitStatus.getCollaboratorSampleId());
        Assert.assertNotNull(kitStatus.getCollaboratorParticipantId());
        List<KitRequestShipping> newKits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", salivaKitType);
        Assert.assertEquals(oldKits.size() + 1, newKits.size());
        KitRequestShipping newKit =
                newKits.stream().filter(kitRequestShipping -> kitRequestShipping.getDdpParticipantId()
                                .equals(juniperTestKitRequest.getJuniperParticipantID()))
                        .findAny().get();
        Assert.assertEquals(newKit.getBspCollaboratorParticipantId(), bspPrefix + "_" + juniperTestKitRequest.getJuniperParticipantID());
        Assert.assertEquals(newKit.getBspCollaboratorSampleId(),
                bspPrefix + "_" + juniperTestKitRequest.getJuniperParticipantID() + "_" + salivaKitType);
    }

    private void verifyStatusKitResponse(KitResponse kitResponse, JuniperKitRequest juniperTestKit, int testRandomNumber,
                                                          String expectedStatus) {
        Assert.assertNotNull(kitResponse);
        Assert.assertNotNull(kitResponse.getKits());
        Assert.assertEquals(1, kitResponse.getKits().size());
        Assert.assertNotNull(
                kitResponse.getKits().stream()
                        .filter(kitStatus -> kitStatus.getJuniperKitId().equals("JuniperTestKitId_" + testRandomNumber))
                        .findAny());
        NonPepperKitStatusDto nonPepperKitStatus = kitResponse.getKits().get(0);
        Assert.assertEquals(juniperTestKit.getJuniperKitId(), nonPepperKitStatus.getJuniperKitId());
        Assert.assertEquals(juniperTestKit.getJuniperParticipantID(), nonPepperKitStatus.getParticipantId());
        Assert.assertTrue(StringUtils.isBlank(nonPepperKitStatus.getErrorMessage()));
        Assert.assertEquals(false, nonPepperKitStatus.getError());
        Assert.assertTrue(StringUtils.isNotBlank(nonPepperKitStatus.getDsmShippingLabel()));
        Assert.assertNotNull(nonPepperKitStatus.getCurrentStatus());
        Assert.assertEquals(expectedStatus, nonPepperKitStatus.getCurrentStatus());
        Assert.assertNotNull(nonPepperKitStatus.getCollaboratorParticipantId());
        Assert.assertNotNull(nonPepperKitStatus.getCollaboratorSampleId());
    }

    private JuniperKitRequest generateJuniperKitRequest(int random) {
        String participantId = "TEST_PARTICIPANT";

        String json = "{ \"firstName\":\"P\","
                + "\"lastName\":\"T\","
                + "\"street1\":\"415 Main st\","
                + "\"street2\":null,"
                + "\"city\":\"Cambridge\","
                + "\"state\":\"MA\","
                + "\"postalCode\":\"02142\","
                + "\"country\":\"USA\","
                + "\"phoneNumber\":\" 111 - 222 - 3344\","
                + "\"juniperKitId\":\"JuniperTestKitId_" + random + "\","
                + "\"juniperParticipantID\":\"" + participantId + random + "\","
                + "\"skipAddressValidation\":false,"
                + "\"juniperStudyID\":\"Juniper-test-guid\"}";

        return new Gson().fromJson(json, JuniperKitRequest.class);
    }

    @Test
    public void createNewJuniperKitTest() {

        int rand = new Random().nextInt() & Integer.MAX_VALUE;
        JuniperKitRequest juniperTestKit = generateJuniperKitRequest(rand);
        createNonPepperTestKit(juniperTestKit);
    }

    @Test
    public void noJuniperKitIdTest() {
        String participantId = "OHSALK_";
        int rand = new Random().nextInt() & Integer.MAX_VALUE;
        String kitType = salivaKitType;
        List<KitRequestShipping> oldkits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", kitType);
        String json = "{ \"firstName\":\"P\","
                + "\"lastName\":\"T\","
                + "\"street1\":\"415 Main st\","
                + "\"street2\":null,"
                + "\"city\":\"Cambridge\","
                + "\"state\":\"MA\","
                + "\"postalCode\":\"02142\","
                + "\"country\":\"USA\","
                + "\"phoneNumber\":\" 111 - 222 - 3344\","
                + "\"juniperKitId\":null,"
                + "\"juniperParticipantID\":\"" + participantId + rand + "\","
                + "\"skipAddressValidation\":false,"
                + "\"juniperStudyID\":\"Juniper-test-guid\"}";

        JuniperKitRequest juniperTestKit = new Gson().fromJson(json, JuniperKitRequest.class);
        createdKitIds.add(juniperTestKit.getJuniperKitId());
        KitResponse kitResponse =
                nonPepperKitCreationService.createNonPepperKit(juniperTestKit, kitType, mockEasyPostUtil, ddpInstance);
        Assert.assertEquals(KitResponse.ErrorMessage.MISSING_JUNIPER_KIT_ID, kitResponse.getErrorMessage());
        Assert.assertEquals(null, kitResponse.getValue());
        List<KitRequestShipping> newKits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", kitType);
        Assert.assertEquals(newKits.size(), oldkits.size());

    }

    @Test
    public void noJuniperParticipantIdTest() {
        String participantId = "";
        int rand = new Random().nextInt() & Integer.MAX_VALUE;
        String kitType = salivaKitType;
        List<KitRequestShipping> oldkits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", kitType);
        String json = "{ \"firstName\":\"P\","
                + "\"lastName\":\"T\","
                + "\"street1\":\"415 Main st\","
                + "\"street2\":null,"
                + "\"city\":\"Cambridge\","
                + "\"state\":\"MA\","
                + "\"postalCode\":\"02142\","
                + "\"country\":\"USA\","
                + "\"phoneNumber\":\" 111 - 222 - 3344\","
                + "\"juniperKitId\":\"JuniperTestKitId_" + rand + "\","
                + "\"juniperParticipantID\":\"" + participantId + "\","
                + "\"skipAddressValidation\":false,"
                + "\"juniperStudyID\":\"Juniper-test-guid\"}";

        JuniperKitRequest juniperTestKit = new Gson().fromJson(json, JuniperKitRequest.class);
        createdKitIds.add(juniperTestKit.getJuniperKitId());
        KitResponse kitResponse =
                nonPepperKitCreationService.createNonPepperKit(juniperTestKit, salivaKitType, mockEasyPostUtil, ddpInstance);
        Assert.assertEquals(KitResponse.ErrorMessage.MISSING_JUNIPER_PARTICIPANT_ID, kitResponse.getErrorMessage());
        Assert.assertEquals("", kitResponse.getValue());
        List<KitRequestShipping> newKits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", kitType);
        Assert.assertEquals(newKits.size(), oldkits.size());
    }

    @Test
    public void invalidKitTypeTest() {
        String participantId = "OHSALK_";
        int rand = new Random().nextInt() & Integer.MAX_VALUE;
        String kitType = "BLOOD";
        try {
            KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", kitType);
            Assert.fail();
        } catch (DsmInternalError e) {
            Assert.assertTrue(e.getMessage().contains(String.format("No kit type was found for study %s with kit type name %s",
                    instanceName, kitType)));
            String json = "{ \"firstName\":\"P\","
                    + "\"lastName\":\"T\","
                    + "\"street1\":\"415 Main st\","
                    + "\"street2\":null,"
                    + "\"city\":\"Cambridge\","
                    + "\"state\":\"MA\","
                    + "\"postalCode\":\"02142\","
                    + "\"country\":\"USA\","
                    + "\"phoneNumber\":\" 111 - 222 - 3344\","
                    + "\"juniperKitId\":\"JuniperTestKitId_" + rand + "\","
                    + "\"juniperParticipantID\":\"" + participantId + rand + "\","
                    + "\"skipAddressValidation\":false,"
                    + "\"juniperStudyID\":\"Juniper-test-guid\"}";

            JuniperKitRequest juniperTestKit = new Gson().fromJson(json, JuniperKitRequest.class);
            createdKitIds.add(juniperTestKit.getJuniperKitId());
            KitResponse kitResponse =
                    nonPepperKitCreationService.createNonPepperKit(juniperTestKit, kitType, mockEasyPostUtil, ddpInstance);
            Assert.assertEquals(KitResponse.ErrorMessage.UNKNOWN_KIT_TYPE, kitResponse.getErrorMessage());
            Assert.assertEquals(kitResponse.getValue(), kitType);
        }
    }

}
