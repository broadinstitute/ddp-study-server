package org.broadinstitute.dsm.juniperkits;

import java.util.List;
import java.util.Random;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import org.broadinstitute.dsm.DSMServer;
import org.broadinstitute.dsm.TestHelper;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.model.nonpepperkit.JuniperKitRequest;
import org.broadinstitute.dsm.model.nonpepperkit.NonPepperKitCreationService;
import org.broadinstitute.dsm.model.nonpepperkit.NonPepperStatusKitService;
import org.broadinstitute.dsm.model.nonpepperkit.StatusKitResponse;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class JuniperKitStatusTest {

    NonPepperStatusKitService nonPepperStatusKitService = new NonPepperStatusKitService();
    NonPepperKitCreationService nonPepperKitCreationService = new NonPepperKitCreationService();
    private Config cfg;

    @Before
    public void beforeClass() {
        TestHelper.setupDB();
        cfg = TestHelper.cfg;

        DSMServer.setupDDPConfigurationLookup(cfg.getString(ApplicationConfigConstants.DDP));

    }

    @Test
    @Ignore
    public void statusForNewJuniperKitTest() {
        String instanceGuid = "Juniper-mock-guid";
        String instanceName = "Juniper-mock";
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
    }
}
