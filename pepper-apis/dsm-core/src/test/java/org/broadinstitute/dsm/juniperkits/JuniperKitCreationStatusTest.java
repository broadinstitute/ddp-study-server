package org.broadinstitute.dsm.juniperkits;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.DbTxnBaseTest;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dto.kit.nonPepperKit.NonPepperKitStatusDto;
import org.broadinstitute.dsm.model.nonpepperkit.JuniperKitRequest;
import org.broadinstitute.dsm.model.nonpepperkit.KitResponse;
import org.broadinstitute.dsm.model.nonpepperkit.NonPepperKitCreationService;
import org.broadinstitute.dsm.model.nonpepperkit.NonPepperStatusKitService;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.EasyPostUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;


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
    NonPepperKitCreationService nonPepperKitCreationService = new NonPepperKitCreationService();
    static EasyPostUtil mockEasyPostUtil = mock(EasyPostUtil.class);

    NonPepperStatusKitService nonPepperStatusKitService = new NonPepperStatusKitService();

    @BeforeClass
    public static void setupJuniperBefore() {
        JuniperSetupUtil juniperSetupUtil = new JuniperSetupUtil(instanceName, instanceGuid, "Juniper-Test", "JuniperTestProject");
        ;
        juniperSetupUtil.setupJuniperInstanceAndSettings();
        ddpInstance = DDPInstance.getDDPInstanceWithRoleByStudyGuid(instanceGuid, DBConstants.JUNIPER_STUDY_INSTANCE_ROLE);
        when(mockEasyPostUtil.checkAddress(any(), anyString())).thenReturn(true);
    }

    @AfterClass
    public static void deleteJuniperInstance() {
        JuniperSetupUtil.deleteKitsArray(createdKitIds);
        JuniperSetupUtil.deleteJuniperInstanceAndSettings();
    }

    @Test
    public void testStatusKitEndpointByJuniperStudyGuid() {
        int rand = new Random().nextInt() & Integer.MAX_VALUE;
        JuniperKitRequest juniperTestKit = generateJuniperKit(rand);

        try {
            createAndAssertNonPepperCreation(juniperTestKit);

            KitResponse kitResponse = nonPepperStatusKitService.getKitsByStudyName(instanceGuid);
            Assert.assertNotNull(kitResponse);
            Assert.assertNotNull(kitResponse.getKits());
            Assert.assertNotEquals(0, kitResponse.getKits().size());
            Assert.assertNotNull(
                    kitResponse.getKits().stream().filter(kitStatus -> kitStatus.getJuniperKitId().equals("JuniperTestKitId_" + rand))
                            .findAny());
        } finally {
            createdKitIds.add(juniperTestKit.getJuniperKitId());
        }
    }

    @Test
    public void testStatusByJuniperKitId() {
        int rand = new Random().nextInt() & Integer.MAX_VALUE;
        JuniperKitRequest juniperTestKit = generateJuniperKit(rand);
        try {
            createAndAssertNonPepperCreation(juniperTestKit);

            KitResponse kitResponse = nonPepperStatusKitService.getKitsBasedOnJuniperKitId(juniperTestKit.getJuniperKitId());

            assertStatusKitResponse(kitResponse, juniperTestKit, rand);
        } finally {
            createdKitIds.add(juniperTestKit.getJuniperKitId());
        }
    }

    @Test
    public void testStatusByKitIdTest() {
        int rand = new Random().nextInt() & Integer.MAX_VALUE;
        JuniperKitRequest juniperTestKit = generateJuniperKit(rand);
        try {
            createAndAssertNonPepperCreation(juniperTestKit);
            KitResponse kitResponse = nonPepperStatusKitService.getKitsBasedOnJuniperKitId(juniperTestKit.getJuniperKitId());
            assertStatusKitResponse(kitResponse, juniperTestKit, rand);

        } finally {
            createdKitIds.add(juniperTestKit.getJuniperKitId());
        }
    }

    private void createAndAssertNonPepperCreation(JuniperKitRequest juniperTestKit) {
        List<KitRequestShipping> oldkits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", salivaKitType);
        KitResponse kitResponse = nonPepperKitCreationService.createNonPepperKit(juniperTestKit, salivaKitType, mockEasyPostUtil, ddpInstance);
        Assert.assertFalse(kitResponse.isError());
        List<KitRequestShipping> newKits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", salivaKitType);
        Assert.assertEquals(oldkits.size() + 1, newKits.size());
    }

    private void assertStatusKitResponse(KitResponse kitResponse, JuniperKitRequest juniperTestKit, int testRandomNumber) {
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
        Assert.assertEquals("", nonPepperKitStatus.getErrorMessage());
        Assert.assertEquals(false, nonPepperKitStatus.getError());
        Assert.assertTrue(StringUtils.isNotBlank(nonPepperKitStatus.getDsmShippingLabel()));
    }

    private JuniperKitRequest generateJuniperKit(int random) {
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
                + "\"forceUpload\":false,"
                + "\"skipAddressValidation\":false,"
                + "\"juniperStudyID\":\"Juniper-test-guid\"}";

        return new Gson().fromJson(json, JuniperKitRequest.class);
    }

    @Test
    public void createNewJuniperKitTest() {
        String participantId = "OHSALK_";
        int rand = new Random().nextInt() & Integer.MAX_VALUE;
        String kitType = salivaKitType;
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
                + "\"forceUpload\":false,"
                + "\"skipAddressValidation\":false,"
                + "\"juniperStudyID\":\"Juniper-test-guid\"}";

        JuniperKitRequest juniperTestKit = new Gson().fromJson(json, JuniperKitRequest.class);
        log.info("Juniper test kit id is {} ", juniperTestKit.getJuniperKitId());
        when(mockEasyPostUtil.checkAddress(any(), anyString())).thenReturn(true);
        try {
            List<KitRequestShipping> oldkits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", kitType);
            KitResponse kitCreationResponse =
                    nonPepperKitCreationService.createNonPepperKit(juniperTestKit, salivaKitType, mockEasyPostUtil, ddpInstance);
            List<KitRequestShipping> newKits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", salivaKitType);
            if (kitCreationResponse.isError()){
                log.error(kitCreationResponse.getErrorMessage().toString());
            }
            Assert.assertFalse(kitCreationResponse.isError());
            Assert.assertEquals(newKits.size(), oldkits.size() + 1);
            KitRequestShipping newKit =
                    newKits.stream().filter(kitRequestShipping -> kitRequestShipping.getDdpParticipantId().equals(participantId + rand))
                            .findAny().get();
            Assert.assertEquals(newKit.getBspCollaboratorParticipantId(), bspPrefix + "_" + participantId + rand);
            Assert.assertEquals(newKit.getBspCollaboratorSampleId(), bspPrefix + "_" + participantId + rand + "_" + kitType);
        } finally {
            createdKitIds.add(juniperTestKit.getJuniperKitId());
        }
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
                + "\"forceUpload\":false,"
                + "\"skipAddressValidation\":false,"
                + "\"juniperStudyID\":\"Juniper-test-guid\"}";

        JuniperKitRequest juniperTestKit = new Gson().fromJson(json, JuniperKitRequest.class);
        try {
            KitResponse kitResponse =
                    nonPepperKitCreationService.createNonPepperKit(juniperTestKit, kitType, mockEasyPostUtil, ddpInstance);
            Assert.assertEquals(KitResponse.ErrorMessage.MISSING_JUNIPER_KIT_ID, kitResponse.getErrorMessage());
            Assert.assertEquals(null, kitResponse.getValue());
            List<KitRequestShipping> newKits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", kitType);
            Assert.assertEquals(newKits.size(), oldkits.size());
        } finally {
            createdKitIds.add(juniperTestKit.getJuniperKitId());
        }
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
                + "\"forceUpload\":false,"
                + "\"skipAddressValidation\":false,"
                + "\"juniperStudyID\":\"Juniper-test-guid\"}";

        JuniperKitRequest juniperTestKit = new Gson().fromJson(json, JuniperKitRequest.class);
        try {
            KitResponse kitResponse =
                    nonPepperKitCreationService.createNonPepperKit(juniperTestKit, salivaKitType, mockEasyPostUtil, ddpInstance);
            Assert.assertEquals(KitResponse.ErrorMessage.MISSING_JUNIPER_PARTICIPANT_ID, kitResponse.getErrorMessage());
            Assert.assertEquals("", kitResponse.getValue());
            List<KitRequestShipping> newKits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", kitType);
            Assert.assertEquals(newKits.size(), oldkits.size());
        } finally {
            createdKitIds.add(juniperTestKit.getJuniperKitId());
        }
    }

    @Test
    public void invalidKitTypeTest() {
        String participantId = "OHSALK_";
        int rand = new Random().nextInt() & Integer.MAX_VALUE;
        String kitType = "BLOOD";
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
                + "\"juniperParticipantID\":\"" + participantId + rand + "\","
                + "\"forceUpload\":false,"
                + "\"skipAddressValidation\":false,"
                + "\"juniperStudyID\":\"Juniper-test-guid\"}";

        JuniperKitRequest juniperTestKit = new Gson().fromJson(json, JuniperKitRequest.class);
        try {
            KitResponse kitResponse =
                    nonPepperKitCreationService.createNonPepperKit(juniperTestKit, kitType, mockEasyPostUtil, ddpInstance);
            Assert.assertEquals(KitResponse.ErrorMessage.UNKNOWN_KIT_TYPE, kitResponse.getErrorMessage());
            Assert.assertEquals(kitResponse.getValue(), kitType);
            List<KitRequestShipping> newKits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", kitType);
            Assert.assertEquals(newKits.size(), oldkits.size());
        } finally {
            createdKitIds.add(juniperTestKit.getJuniperKitId());
        }
    }

}
