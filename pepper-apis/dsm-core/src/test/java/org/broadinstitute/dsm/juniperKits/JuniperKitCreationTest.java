package org.broadinstitute.dsm.juniperKits;


import com.google.gson.Gson;
import com.typesafe.config.Config;
import org.broadinstitute.dsm.DSMServer;
import org.broadinstitute.dsm.TestHelper;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.model.notPepperKit.JuniperKitRequest;
import org.broadinstitute.dsm.model.notPepperKit.KitResponse;
import org.broadinstitute.dsm.model.notPepperKit.NotPepperKitCreationService;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;
import java.util.Random;

public class JuniperKitCreationTest {
    private static Config cfg;
    NotPepperKitCreationService notPepperKitCreationService = new NotPepperKitCreationService();

    @Before
    public void beforeClass() {
        TestHelper.setupDB();
        cfg = TestHelper.cfg;

        DSMServer.setupDDPConfigurationLookup(cfg.getString(ApplicationConfigConstants.DDP));
    }

    @Test
    @Ignore
    public void createNewMockJuniperKitTest() {
        String instanceGuid = "Juniper-mock-guid";
        String instanceName = "Juniper-mock";
        String participantId = "OHSALK_";
        int rand = new Random().nextInt() & Integer.MAX_VALUE;

        String json = "{ \"firstName\":\"P\"," +
                "\"lastName\":\"T\"," +
                "\"street1\":\"415 Main st\"," +
                "\"street2\":null," +
                "\"city\":\"Cambridge\"," +
                "\"state\":\"MA\"," +
                "\"postalCode\":\"02142\"," +
                "\"country\":\"USA\"," +
                "\"phoneNumber\":\" 111 - 222 - 3344\"," +
                "\"juniperKitId\":\"JuniperTestKitId_" + rand + "\"," +
                "\"juniperParticipantID\":\"" + participantId + rand + "\"," +
                "\"forceUpload\":false," +
                "\"noAddressValidation\":false," +
                "\"juniperStudyID\":\"Juniper-mock-guid\"}";

//        String json3 = "{\"firstName\":\"P\",\"lastName\":\"T\",\"street1\":\"415 Main st\",\"street2\":\"\",\"city\":\"Cambridge\",\"state\":\"MA\",\"postalCode\":\"02142\",\"country\":\"USA\",\"phoneNumber\":\"111-222-3344\",\"juniperKitId\":\"JuniperTestKitId_110958866\",\"juniperParticipantID\":\"JuniperParticipantId_110958866\",\"juniperStudyID\":\"Juniper-mock-guid\"}";

        JuniperKitRequest mockJuniperKit = new Gson().fromJson(json, JuniperKitRequest.class);
        List<KitRequestShipping> oldkits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", "SALIVA");

//        JuniperKitRequest
//                mockJuniperKit = new JuniperKitRequest("P", "T", "415 Main st", "", "Cambridge", "MA", "02142", "USA", "111-222-3344",
//                "JuniperTestKitId_" + rand, participantId + rand, instanceGuid);
//        KitUploadObject juniperKitToUpload = new KitUploadObject(null, mockJuniperKit.getJuniperParticipantID(),
//                mockJuniperKit.getJuniperParticipantID(), mockJuniperKit.getFirstName(), mockJuniperKit.getLastName(), mockJuniperKit.getStreet1(),
//                mockJuniperKit.getStreet2(), mockJuniperKit.getCity(), mockJuniperKit.getState(), mockJuniperKit.getPostalCode(), mockJuniperKit.getCountry(),
//                mockJuniperKit.getPhoneNumber());
//        juniperKitToUpload.setJuniperKitId(mockJuniperKit.getJuniperKitId());
        notPepperKitCreationService.createNonPepperKit(mockJuniperKit, instanceGuid, "SALIVA");
        List<KitRequestShipping> newKits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", "SALIVA");
        Assert.assertEquals(newKits.size(), oldkits.size() + 1);
    }

    @Test
    @Ignore
    public void noJuniperKitIdTest() {
        String instanceGuid = "Juniper-mock-guid";
        String instanceName = "Juniper-mock";
        String participantId = "OHSALK_";

        int rand = new Random().nextInt() & Integer.MAX_VALUE;

        String kitType = "SALIVA";
        List<KitRequestShipping> oldkits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", kitType);

        String json = "{ \"firstName\":\"P\"," +
                "\"lastName\":\"T\"," +
                "\"street1\":\"415 Main st\"," +
                "\"street2\":null," +
                "\"city\":\"Cambridge\"," +
                "\"state\":\"MA\"," +
                "\"postalCode\":\"02142\"," +
                "\"country\":\"USA\"," +
                "\"phoneNumber\":\" 111 - 222 - 3344\"," +
                "\"juniperKitId\":null," +
                "\"juniperParticipantID\":\"" + participantId + rand + "\"," +
                "\"forceUpload\":false," +
                "\"noAddressValidation\":false," +
                "\"juniperStudyID\":\"Juniper-mock-guid\"}";


        JuniperKitRequest mockJuniperKit = new Gson().fromJson(json, JuniperKitRequest.class);

        KitResponse kitResponse = notPepperKitCreationService.createNonPepperKit(mockJuniperKit, instanceGuid, kitType);
        Assert.assertEquals(kitResponse.errorMessage, NotPepperKitCreationService.MISSING_JUNIPER_KIT_ID);

        List<KitRequestShipping> newKits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", kitType);
        Assert.assertEquals(newKits.size(), oldkits.size());
    }

    @Test
    @Ignore
    public void noJuniperParticipantIdTest() {
        String instanceGuid = "Juniper-mock-guid";
        String instanceName = "Juniper-mock";
        String participantId = "";
        int rand = new Random().nextInt() & Integer.MAX_VALUE;

        String kitType = "SALIVA";
        List<KitRequestShipping> oldkits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", kitType);


        String json = "{ \"firstName\":\"P\"," +
                "\"lastName\":\"T\"," +
                "\"street1\":\"415 Main st\"," +
                "\"street2\":null," +
                "\"city\":\"Cambridge\"," +
                "\"state\":\"MA\"," +
                "\"postalCode\":\"02142\"," +
                "\"country\":\"USA\"," +
                "\"phoneNumber\":\" 111 - 222 - 3344\"," +
                "\"juniperKitId\":\"JuniperTestKitId_" + rand + "\"," +
                "\"juniperParticipantID\":\"" + participantId + "\"," +
                "\"forceUpload\":false," +
                "\"noAddressValidation\":false," +
                "\"juniperStudyID\":\"Juniper-mock-guid\"}";


        JuniperKitRequest mockJuniperKit = new Gson().fromJson(json, JuniperKitRequest.class);

        KitResponse kitResponse = notPepperKitCreationService.createNonPepperKit(mockJuniperKit, instanceGuid, "SALIVA");
        Assert.assertEquals(kitResponse.errorMessage, NotPepperKitCreationService.MISSING_JUNIPER_PARTICIPANT_ID);

        List<KitRequestShipping> newKits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", kitType);
        Assert.assertEquals(newKits.size(), oldkits.size());
    }

    @Test
    @Ignore
    public void invalidKitTypeTest() {
        String instanceGuid = "Juniper-mock-guid";
        String instanceName = "Juniper-mock";
        String participantId = "OHSALK_";
        int rand = new Random().nextInt() & Integer.MAX_VALUE;

        String kitType = "BLOOD";
        List<KitRequestShipping> oldkits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", kitType);


        String json = "{ \"firstName\":\"P\"," +
                "\"lastName\":\"T\"," +
                "\"street1\":\"415 Main st\"," +
                "\"street2\":null," +
                "\"city\":\"Cambridge\"," +
                "\"state\":\"MA\"," +
                "\"postalCode\":\"02142\"," +
                "\"country\":\"USA\"," +
                "\"phoneNumber\":\" 111 - 222 - 3344\"," +
                "\"juniperKitId\":\"JuniperTestKitId_" + rand + "\"," +
                "\"juniperParticipantID\":\"" + participantId + rand + "\"," +
                "\"forceUpload\":false," +
                "\"noAddressValidation\":false," +
                "\"juniperStudyID\":\"Juniper-mock-guid\"}";


        JuniperKitRequest mockJuniperKit = new Gson().fromJson(json, JuniperKitRequest.class);

        KitResponse kitResponse = notPepperKitCreationService.createNonPepperKit(mockJuniperKit, instanceGuid, kitType);
        Assert.assertEquals(kitResponse.errorMessage, NotPepperKitCreationService.UNKNOWN_KIT_TYPE);

        List<KitRequestShipping> newKits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", kitType);
        Assert.assertEquals(newKits.size(), oldkits.size());
    }

    @Test
    @Ignore
    public void invalidStudyNameTest() {
        String fakeInstanceGuid = "Juniper-mock-guid-fake";
        String instanceName = "Juniper-mock";
        String participantId = "OHSALK_";
        int rand = new Random().nextInt() & Integer.MAX_VALUE;

        String kitType = "SALIVA";
        List<KitRequestShipping> oldkits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", kitType);


        String json = "{ \"firstName\":\"P\"," +
                "\"lastName\":\"T\"," +
                "\"street1\":\"415 Main st\"," +
                "\"street2\":null," +
                "\"city\":\"Cambridge\"," +
                "\"state\":\"MA\"," +
                "\"postalCode\":\"02142\"," +
                "\"country\":\"USA\"," +
                "\"phoneNumber\":\" 111 - 222 - 3344\"," +
                "\"juniperKitId\":\"JuniperTestKitId_" + rand + "\"," +
                "\"juniperParticipantID\":\"" + participantId + rand + "\"," +
                "\"forceUpload\":false," +
                "\"noAddressValidation\":false," +
                "\"juniperStudyID\":\"Juniper-mock-guid\"}";


        JuniperKitRequest mockJuniperKit = new Gson().fromJson(json, JuniperKitRequest.class);

        KitResponse kitResponse = notPepperKitCreationService.createNonPepperKit(mockJuniperKit, fakeInstanceGuid, kitType);
        Assert.assertEquals(kitResponse.errorMessage, NotPepperKitCreationService.UNKNOWN_STUDY);

        List<KitRequestShipping> newKits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", kitType);
        Assert.assertEquals(newKits.size(), oldkits.size());
    }

}
