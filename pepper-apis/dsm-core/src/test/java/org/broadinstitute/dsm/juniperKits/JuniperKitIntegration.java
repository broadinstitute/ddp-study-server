package org.broadinstitute.dsm.juniperKits;


import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import com.typesafe.config.Config;
import org.broadinstitute.dsm.DSMServer;
import org.broadinstitute.dsm.TestHelper;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.model.JuniperKitRequest;
import org.broadinstitute.dsm.model.KitRequest;
import org.broadinstitute.dsm.model.KitUploadObject;
import org.broadinstitute.dsm.route.JuniperShipKitRoute;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class JuniperKitIntegration {
    private static Config cfg;

    private static JuniperShipKitRoute route;
    @Before
    public void beforeClass(){
        TestHelper.setupDB();
        cfg = TestHelper.cfg;

        route = new JuniperShipKitRoute();
        DSMServer.setupDDPConfigurationLookup(cfg.getString(ApplicationConfigConstants.DDP));
    }

    @Test
    @Ignore
    public void createNewMockJuniperKit() {
        String instanceGuid = "Juniper-mock-guid";
        String instanceName = "Juniper-mock";
        String participantId = "JuniperParticipantId";
        DDPInstance ddpInstance = DDPInstance.getDDPInstance(instanceName);
        List<KitRequestShipping> oldkits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", "SALIVA");
        int rand = new Random().nextInt();
        JuniperKitRequest
                mockJuniperKit = new JuniperKitRequest("P", "T", "415 Main st", "", "Cambridge", "MA", "02142", "USA", "111-222-3344",
                "JuniperTestKitId_" + rand, participantId, instanceGuid);
        KitUploadObject juniperKitToUpload = new KitUploadObject(null, mockJuniperKit.getJuniperParticipantID(),
                mockJuniperKit.getJuniperParticipantID(), mockJuniperKit.getFirstName(), mockJuniperKit.getLastName(), mockJuniperKit.getStreet1(),
                mockJuniperKit.getStreet2(), mockJuniperKit.getCity(), mockJuniperKit.getState(), mockJuniperKit.getPostalCode(), mockJuniperKit.getCountry(),
                mockJuniperKit.getPhoneNumber());
        juniperKitToUpload.setJuniperKitId(mockJuniperKit.getJuniperKitId());
        List<KitRequest> juniperKits = new ArrayList<>();
        juniperKits.add(juniperKitToUpload);
        route.createAJuniperKit(juniperKits, instanceGuid, "SALIVA", false, "61", new AtomicReference());
        List<KitRequestShipping> newKits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", "SALIVA");
        Assert.assertEquals(newKits.size(), oldkits.size() + 1);
    }

}
