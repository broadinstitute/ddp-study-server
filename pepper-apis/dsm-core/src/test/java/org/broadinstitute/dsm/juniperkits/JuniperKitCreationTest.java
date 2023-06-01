package org.broadinstitute.dsm.juniperkits;

import java.util.List;
import java.util.Random;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import org.broadinstitute.dsm.DSMServer;
import org.broadinstitute.dsm.TestHelper;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.model.nonpepperkit.JuniperKitRequest;
import org.broadinstitute.dsm.model.nonpepperkit.KitResponse;
import org.broadinstitute.dsm.model.nonpepperkit.NonPepperKitCreationService;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class JuniperKitCreationTest {
    NonPepperKitCreationService nonPepperKitCreationService = new NonPepperKitCreationService();
    private Config cfg;

    @Before
    public void beforeClass() {
        TestHelper.setupDB();
        cfg = TestHelper.cfg;

        DSMServer.setupDDPConfigurationLookup(cfg.getString(ApplicationConfigConstants.DDP));
    }

    @Test
//    @Ignore
    public void createNewMockJuniperKitTest() {
        String instanceGuid = "Juniper-mock-guid";
        String instanceName = "Juniper-mock";
        String participantId = "OHSALK_";
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

        //        String json3 = "{\"firstName\":\"P\",\"lastName\":\"T\",\"street1\":\"415 Main st\",\"street2\":\"\",\"city\":
        //        \"Cambridge\",
        //        \"state\":\"MA\",\"postalCode\":\"02142\",\"country\":\"USA\",\"phoneNumber\":\"111-222-3344\",\"juniperKitId\":
        //        \"JuniperTestKitId_110958866\",\"juniperParticipantID\":\"JuniperParticipantId_110958866\",\"juniperStudyID\":
        //        \"Juniper-mock-guid\"}";

        JuniperKitRequest mockJuniperKit = new Gson().fromJson(json, JuniperKitRequest.class);
        List<KitRequestShipping> oldkits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", kitType);

        nonPepperKitCreationService.createNonPepperKit(mockJuniperKit, instanceGuid, "SALIVA");
        List<KitRequestShipping> newKits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", "SALIVA");
        Assert.assertEquals(newKits.size(), oldkits.size() + 1);
        KitRequestShipping newKit = newKits.stream().filter(kitRequestShipping -> kitRequestShipping.getDdpParticipantId().equals(participantId + rand)).findAny()
                .get();
        Assert.assertEquals(newKit.getBspCollaboratorParticipantId(), "JuniperProject_" + participantId + rand);
        Assert.assertEquals(newKit.getBspCollaboratorSampleId(), "JuniperProject_" + participantId + rand + "_" + kitType);
    }

    @Test
//    @Ignore
    public void noJuniperKitIdTest() {
        String instanceGuid = "Juniper-mock-guid";
        String instanceName = "Juniper-mock";
        String participantId = "OHSALK_";

        int rand = new Random().nextInt() & Integer.MAX_VALUE;

        String kitType = "SALIVA";
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

        KitResponse kitResponse = nonPepperKitCreationService.createNonPepperKit(mockJuniperKit, instanceGuid, kitType);
        Assert.assertEquals(kitResponse.errorMessage, NonPepperKitCreationService.MISSING_JUNIPER_KIT_ID);
        Assert.assertEquals(kitResponse.value, null);

        List<KitRequestShipping> newKits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", kitType);
        Assert.assertEquals(newKits.size(), oldkits.size());
    }

    @Test
//    @Ignore
    public void noJuniperParticipantIdTest() {
        String instanceGuid = "Juniper-mock-guid";
        String instanceName = "Juniper-mock";
        String participantId = "";
        int rand = new Random().nextInt() & Integer.MAX_VALUE;

        String kitType = "SALIVA";
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

        KitResponse kitResponse = nonPepperKitCreationService.createNonPepperKit(mockJuniperKit, instanceGuid, "SALIVA");
        Assert.assertEquals(kitResponse.errorMessage, NonPepperKitCreationService.MISSING_JUNIPER_PARTICIPANT_ID);
        Assert.assertEquals(kitResponse.value, "");

        List<KitRequestShipping> newKits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", kitType);
        Assert.assertEquals(newKits.size(), oldkits.size());
    }

    @Test
//    @Ignore
    public void invalidKitTypeTest() {
        String instanceGuid = "Juniper-mock-guid";
        String instanceName = "Juniper-mock";
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

        KitResponse kitResponse = nonPepperKitCreationService.createNonPepperKit(mockJuniperKit, instanceGuid, kitType);
        Assert.assertEquals(kitResponse.errorMessage, NonPepperKitCreationService.UNKNOWN_KIT_TYPE);
        Assert.assertEquals(kitResponse.value, kitType);

        List<KitRequestShipping> newKits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", kitType);
        Assert.assertEquals(newKits.size(), oldkits.size());
    }

    @Test
//    @Ignore
    public void invalidStudyNameTest() {
        String fakeInstanceGuid = "Juniper-mock-guid-fake";
        String instanceName = "Juniper-mock";
        String participantId = "OHSALK_";
        int rand = new Random().nextInt() & Integer.MAX_VALUE;

        String kitType = "SALIVA";
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

        KitResponse kitResponse = nonPepperKitCreationService.createNonPepperKit(mockJuniperKit, fakeInstanceGuid, kitType);
        Assert.assertEquals(kitResponse.errorMessage, NonPepperKitCreationService.UNKNOWN_STUDY);
        Assert.assertEquals(kitResponse.value, fakeInstanceGuid);

        List<KitRequestShipping> newKits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", kitType);
        Assert.assertEquals(newKits.size(), oldkits.size());
    }

}
