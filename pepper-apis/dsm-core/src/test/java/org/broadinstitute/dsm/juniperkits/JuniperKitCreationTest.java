package org.broadinstitute.dsm.juniperkits;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Random;

import com.google.gson.Gson;
import org.broadinstitute.dsm.DbTxnBaseTest;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.model.nonpepperkit.JuniperKitRequest;
import org.broadinstitute.dsm.model.nonpepperkit.KitResponse;
import org.broadinstitute.dsm.model.nonpepperkit.KitResponseError;
import org.broadinstitute.dsm.model.nonpepperkit.NonPepperKitCreationService;
import org.broadinstitute.dsm.util.EasyPostUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;


/**
 * Tests the NonPepperKitCreationService class
 * To run this class use the following VM variables
 * -ea -Dconfig.file=[path to /pepper-apis/output-build-config/testing-inmemorydb.conf]
 */

@RunWith(MockitoJUnitRunner.class)
public class JuniperKitCreationTest extends DbTxnBaseTest {

    final String instanceGuid = "Juniper-mock-guid";
    final String instanceName = "Juniper-mock";
    final String salivaKitType = "SALIVA";
    final String bspPrefix = "JuniperTestProject";
    NonPepperKitCreationService nonPepperKitCreationService = new NonPepperKitCreationService();
    DDPInstance ddpInstance;
    EasyPostUtil mockEasyPostUtil = mock(EasyPostUtil.class);


    @Before
    public void setupJuniperBefore() {
        JuniperSetupUtil.setupUpAJuniperInstance(instanceName, instanceGuid, "Juniper-Mock", bspPrefix);
        JuniperSetupUtil.loadDSMConfig();
        ddpInstance = DDPInstance.getDDPInstanceWithRoleByStudyGuid(instanceGuid, "juniper_study");
    }

    @After
    public void deleteJuniperInstance() {
        JuniperSetupUtil.deleteJuniperTestStudies();
    }

    @Test
    public void createNewMockJuniperKitTest() {
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
                + "\"juniperStudyID\":\"Juniper-mock-guid\"}";

        JuniperKitRequest mockJuniperKit = new Gson().fromJson(json, JuniperKitRequest.class);
        when(mockEasyPostUtil.checkAddress(any(), anyString())).thenReturn(true);
        try {
            List<KitRequestShipping> oldkits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", kitType);
            KitResponse kitCreationResponse = nonPepperKitCreationService.createNonPepperKit(mockJuniperKit, salivaKitType,
                    mockEasyPostUtil, ddpInstance);
            List<KitRequestShipping> newKits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", salivaKitType);
            Assert.assertEquals(newKits.size(), oldkits.size() + 1);
            KitRequestShipping newKit =
                    newKits.stream().filter(kitRequestShipping -> kitRequestShipping.getDdpParticipantId().equals(participantId + rand))
                            .findAny().get();
            Assert.assertEquals(newKit.getBspCollaboratorParticipantId(), bspPrefix + "_" + participantId + rand);
            Assert.assertEquals(newKit.getBspCollaboratorSampleId(), bspPrefix + "_" + participantId + rand + "_" + kitType);
        } finally {
            JuniperSetupUtil.deleteJuniperKit(mockJuniperKit.getJuniperKitId());
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
                + "\"juniperStudyID\":\"Juniper-mock-guid\"}";

        JuniperKitRequest mockJuniperKit = new Gson().fromJson(json, JuniperKitRequest.class);
        try {
            KitResponseError kitResponse = (KitResponseError) nonPepperKitCreationService.createNonPepperKit(mockJuniperKit, kitType,
                    mockEasyPostUtil, ddpInstance);
            Assert.assertEquals(kitResponse.errorMessage, KitResponseError.ErrorMessage.MISSING_JUNIPER_KIT_ID);
            Assert.assertEquals(kitResponse.value, null);
            List<KitRequestShipping> newKits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", kitType);
            Assert.assertEquals(newKits.size(), oldkits.size());
        } finally {
            JuniperSetupUtil.deleteJuniperKit(mockJuniperKit.getJuniperKitId());
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
                + "\"juniperStudyID\":\"Juniper-mock-guid\"}";

        JuniperKitRequest mockJuniperKit = new Gson().fromJson(json, JuniperKitRequest.class);
        try {
            KitResponseError kitResponse = (KitResponseError) nonPepperKitCreationService.createNonPepperKit(mockJuniperKit, salivaKitType,
                    mockEasyPostUtil, ddpInstance);
            Assert.assertEquals(kitResponse.errorMessage, KitResponseError.ErrorMessage.MISSING_JUNIPER_PARTICIPANT_ID);
            Assert.assertEquals(kitResponse.value, "");
            List<KitRequestShipping> newKits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", kitType);
            Assert.assertEquals(newKits.size(), oldkits.size());
        } finally {
            JuniperSetupUtil.deleteJuniperKit(mockJuniperKit.getJuniperKitId());
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
                + "\"juniperStudyID\":\"Juniper-mock-guid\"}";

        JuniperKitRequest mockJuniperKit = new Gson().fromJson(json, JuniperKitRequest.class);
        try {
            KitResponseError kitResponse = (KitResponseError) nonPepperKitCreationService.createNonPepperKit(mockJuniperKit, kitType,
                    mockEasyPostUtil, ddpInstance);
            Assert.assertEquals(kitResponse.errorMessage, KitResponseError.ErrorMessage.UNKNOWN_KIT_TYPE);
            Assert.assertEquals(kitResponse.value, kitType);
            List<KitRequestShipping> newKits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", kitType);
            Assert.assertEquals(newKits.size(), oldkits.size());
        } finally {
            JuniperSetupUtil.deleteJuniperKit(mockJuniperKit.getJuniperKitId());
        }
    }

}
