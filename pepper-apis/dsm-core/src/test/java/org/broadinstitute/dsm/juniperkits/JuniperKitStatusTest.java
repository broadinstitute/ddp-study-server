package org.broadinstitute.dsm.juniperkits;

import java.util.List;
import java.util.Random;

import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.DbTxnBaseTest;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.model.nonpepperkit.JuniperKitRequest;
import org.broadinstitute.dsm.model.nonpepperkit.NonPepperKitCreationService;
import org.broadinstitute.dsm.db.dto.kit.nonPepperKit.NonPepperKitStatusDto;
import org.broadinstitute.dsm.model.nonpepperkit.NonPepperStatusKitService;
import org.broadinstitute.dsm.model.nonpepperkit.StatusKitResponse;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class JuniperKitStatusTest extends DbTxnBaseTest {

    final String instanceGuid = "Juniper-mock-guid";
    final String instanceName = "Juniper-mock";
    NonPepperStatusKitService nonPepperStatusKitService = new NonPepperStatusKitService();
    NonPepperKitCreationService nonPepperKitCreationService = new NonPepperKitCreationService();

    @Before
    public void setupJuniperBefore() {
        JuniperSetupUtil.setupUpAJuniperInstance(instanceName, instanceGuid, "Juniper-Mock", "JuniperTestProject");
        JuniperSetupUtil.loadDSMConfig();
    }

    @After
    public void deleteJuniperInstance() {
        JuniperSetupUtil.deleteJuniperTestStudies();
    }

    @Test
    public void statusForJuniperStudy() {
        String participantId = "TEST_PARTICIPANT";
        int rand = new Random().nextInt() & Integer.MAX_VALUE;
        String kitType = "SALIVA";

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
        List<KitRequestShipping> oldkits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", kitType);

        nonPepperKitCreationService.createNonPepperKit(mockJuniperKit, instanceGuid, "SALIVA");
        List<KitRequestShipping> newKits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", "SALIVA");
        Assert.assertEquals(newKits.size(), oldkits.size() + 1);

        StatusKitResponse kitResponse = (StatusKitResponse) nonPepperStatusKitService.getKitsBasedOnStudyName(instanceGuid);
        Assert.assertNotNull(kitResponse);
        Assert.assertNotNull(kitResponse.getKits());
        Assert.assertNotEquals(0, kitResponse.getKits().size());
        Assert.assertNotNull(
                kitResponse.getKits().stream().filter(kitStatus -> kitStatus.getJuniperKitId().equals("JuniperTestKitId_" + rand))
                        .findAny());
        for (NonPepperKitStatusDto nonPepperKitStatusDto : kitResponse.getKits()) {
            JuniperSetupUtil.deleteJuniperKit(nonPepperKitStatusDto.getJuniperKitId());
        }
    }

    @Test
    public void statusByJuniperKitId() {
        String participantId = "TEST_PARTICIPANT";
        int rand = new Random().nextInt() & Integer.MAX_VALUE;
        String kitType = "SALIVA";

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
        List<KitRequestShipping> oldkits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", kitType);

        nonPepperKitCreationService.createNonPepperKit(mockJuniperKit, instanceGuid, "SALIVA");
        List<KitRequestShipping> newKits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", "SALIVA");
        Assert.assertEquals(newKits.size(), oldkits.size() + 1);

        StatusKitResponse kitResponse =
                (StatusKitResponse) nonPepperStatusKitService.getKitsBasedOnJuniperKitId(mockJuniperKit.getJuniperKitId());
        Assert.assertNotNull(kitResponse);
        Assert.assertNotNull(kitResponse.getKits());
        Assert.assertEquals(1, kitResponse.getKits().size());
        Assert.assertNotNull(
                kitResponse.getKits().stream().filter(kitStatus -> kitStatus.getJuniperKitId().equals("JuniperTestKitId_" + rand))
                        .findAny());
        NonPepperKitStatusDto nonPepperKitStatus = kitResponse.getKits().get(0);
        Assert.assertEquals(nonPepperKitStatus.getJuniperKitId(), mockJuniperKit.getJuniperKitId());
        Assert.assertEquals(nonPepperKitStatus.getParticipantId(), mockJuniperKit.getJuniperParticipantID());
        Assert.assertEquals(nonPepperKitStatus.getErrorMessage(), "");
        Assert.assertEquals(nonPepperKitStatus.getError(), false);
        Assert.assertTrue(StringUtils.isNotBlank(nonPepperKitStatus.getDsmShippingLabel()));
        JuniperSetupUtil.deleteJuniperKit(nonPepperKitStatus.getJuniperKitId());
    }

    @Test
    public void statusByKitIdTest() {
        String participantId = "TEST_PARTICIPANT";
        int rand = new Random().nextInt() & Integer.MAX_VALUE;
        String kitType = "SALIVA";

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
        List<KitRequestShipping> oldkits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", kitType);

        nonPepperKitCreationService.createNonPepperKit(mockJuniperKit, instanceGuid, "SALIVA");
        List<KitRequestShipping> newKits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", "SALIVA");
        Assert.assertEquals(newKits.size(), oldkits.size() + 1);

        StatusKitResponse kitResponse = (StatusKitResponse) nonPepperStatusKitService.getKitsBasedOnJuniperKitId(
                mockJuniperKit.getJuniperKitId());

        Assert.assertNotNull(kitResponse);
        Assert.assertNotNull(kitResponse.getKits());
        Assert.assertEquals(1, kitResponse.getKits().size());
        Assert.assertNotNull(
                kitResponse.getKits().stream().filter(kitStatus -> kitStatus.getJuniperKitId().equals("JuniperTestKitId_" + rand))
                        .findAny());
        NonPepperKitStatusDto nonPepperKitStatus = kitResponse.getKits().get(0);
        Assert.assertEquals(nonPepperKitStatus.getJuniperKitId(), mockJuniperKit.getJuniperKitId());
        Assert.assertEquals(nonPepperKitStatus.getParticipantId(), mockJuniperKit.getJuniperParticipantID());
        Assert.assertEquals(nonPepperKitStatus.getErrorMessage(), "");
        Assert.assertEquals(nonPepperKitStatus.getError(), false);
        Assert.assertTrue(StringUtils.isNotBlank(nonPepperKitStatus.getDsmShippingLabel()));
        JuniperSetupUtil.deleteJuniperKit(nonPepperKitStatus.getJuniperKitId());
    }
}
