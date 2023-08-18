package org.broadinstitute.dsm.juniperkits;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.DbTxnBaseTest;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dto.kit.nonPepperKit.NonPepperKitStatusDto;
import org.broadinstitute.dsm.model.nonpepperkit.JuniperKitRequest;
import org.broadinstitute.dsm.model.nonpepperkit.NonPepperKitCreationService;
import org.broadinstitute.dsm.model.nonpepperkit.NonPepperStatusKitService;
import org.broadinstitute.dsm.model.nonpepperkit.StatusKitResponse;
import org.broadinstitute.dsm.util.EasyPostUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * Tests the NonPepperStatusKitService class
 * To run this class use the following VM variables
 * -ea -Dconfig.file=[path to /pepper-apis/output-build-config/testing-inmemorydb.conf]
 */

@RunWith(MockitoJUnitRunner.class)
public class JuniperKitStatusTest extends DbTxnBaseTest {

    static final String instanceGuid = "Juniper-test-guid";
    static final String instanceName = "Juniper-test";
    final String kitType = "SALIVA";
    static DDPInstance ddpInstance;
    static EasyPostUtil mockEasyPostUtil = mock(EasyPostUtil.class);
    static List<String> createdKitIds = new ArrayList<>();
    NonPepperStatusKitService nonPepperStatusKitService = new NonPepperStatusKitService();
    NonPepperKitCreationService nonPepperKitCreationService = new NonPepperKitCreationService();

    @BeforeClass
    public static void setupJuniperBefore() {
        JuniperSetupUtil juniperSetupUtil = new JuniperSetupUtil(instanceName, instanceGuid, "Juniper-Test", "JuniperTestProject");;
        juniperSetupUtil.setupJuniperInstance();
        ddpInstance = DDPInstance.getDDPInstanceWithRoleByStudyGuid(instanceGuid, "juniper_study");
        when(mockEasyPostUtil.checkAddress(any(), anyString())).thenReturn(true);
    }

    @AfterClass
    public static void deleteJuniperInstance() {
        JuniperSetupUtil.deleteKitsArray(createdKitIds);
        JuniperSetupUtil.deleteJuniperTestStudies();
    }

    @Test
    public void testStatusKitEndpointByJuniperStudyGuid() {
        int rand = new Random().nextInt() & Integer.MAX_VALUE;
        JuniperKitRequest juniperTestKit = generateJuniperKit(rand);

        try {
            createAndAssertNonPepperCreation(juniperTestKit);

            StatusKitResponse kitResponse = (StatusKitResponse) nonPepperStatusKitService.getKitsByStudyName(instanceGuid);
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

            StatusKitResponse kitResponse =
                    (StatusKitResponse) nonPepperStatusKitService.getKitsBasedOnJuniperKitId(juniperTestKit.getJuniperKitId());

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

            StatusKitResponse kitResponse = (StatusKitResponse) nonPepperStatusKitService.getKitsBasedOnJuniperKitId(
                    juniperTestKit.getJuniperKitId());
            assertStatusKitResponse(kitResponse, juniperTestKit, rand);

        } finally {
            createdKitIds.add(juniperTestKit.getJuniperKitId());
        }
    }

    private void createAndAssertNonPepperCreation(JuniperKitRequest juniperTestKit) {
        List<KitRequestShipping> oldkits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", kitType);
        nonPepperKitCreationService.createNonPepperKit(juniperTestKit, kitType, mockEasyPostUtil, ddpInstance);
        List<KitRequestShipping> newKits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", kitType);
        Assert.assertEquals(newKits.size(), oldkits.size() + 1);
    }

    private void assertStatusKitResponse(StatusKitResponse kitResponse, JuniperKitRequest juniperTestKit, int testRandomNumber) {
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

    private JuniperKitRequest generateJuniperKit(int random){
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
}
