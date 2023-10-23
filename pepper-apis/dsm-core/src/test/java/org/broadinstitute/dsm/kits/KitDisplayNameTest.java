package org.broadinstitute.dsm.kits;

import java.util.List;

import com.google.gson.Gson;
import org.broadinstitute.dsm.DbTxnBaseTest;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.juniperkits.KitUtil;
import org.broadinstitute.dsm.model.nonpepperkit.JuniperKitRequest;
import org.broadinstitute.dsm.model.nonpepperkit.NonPepperKitCreationService;
import org.broadinstitute.dsm.statics.DBConstants;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class KitDisplayNameTest extends DbTxnBaseTest {
    private static String guid = "TEST_GUID";
    private static String BLOOD_RNA_KIT_TYPE_NAME = "BLOOD";
    private static String BLOOD_RNA_KIT_TYPE_DISPLAY_NAME = "BLOOD and RNA";
    static String[] subkitSettingsIds = null;

    private static KitUtil
            KitUtil = new KitUtil("test_instance", "test_instance_guid", "some_prefix", "test_group", BLOOD_RNA_KIT_TYPE_NAME);

    @BeforeClass
    public static void setupBefore() {
        KitUtil.setupInstanceAndSettings();
    }

    @AfterClass
    public static void cleanUpAfterClass() {
        KitUtil.deleteKitsArray();
        if (subkitSettingsIds != null){
            KitUtil.deleteSubKitSettings(subkitSettingsIds);
        }
        KitUtil.deleteInstanceAndSettings();

    }

    @Test
    public void testSubKitWithDisplayNAme(){
        subkitSettingsIds = KitUtil.createSubKitsForTheStudy("BLOOD",  "BLOOD and RNA", 0, "RNA", null, 1);
        Assert.assertEquals(2, subkitSettingsIds.length);
        NonPepperKitCreationService nonPepperKitCreationService = new NonPepperKitCreationService();
        DDPInstance ddpInstance = DDPInstance.getDDPInstanceWithRole("test_instance",DBConstants.JUNIPER_STUDY_INSTANCE_ROLE);
        JuniperKitRequest juniperTestKit = generateKitRequestJson();
        KitUtil.createNonPepperTestKit(juniperTestKit, nonPepperKitCreationService, ddpInstance);
        List<KitRequestShipping> kits = KitRequestShipping.getKitRequestsByRealm("test_instance", "overview", BLOOD_RNA_KIT_TYPE_NAME);
        Assert.assertEquals(1, kits.size());
        Assert.assertEquals("BLOOD", kits.get(0).getKitTypeName());
        Assert.assertEquals("BLOOD and RNA", kits.get(0).getDisplayName());

    }

    @Test
    public void testEmptyDisplayName() {
        KitRequestShipping kitWithDisplayName = new KitRequestShipping(guid, "TestProject_2", null, "FAKE_DSM_LABEL_UID", "study", BLOOD_RNA_KIT_TYPE_NAME, 1L, 1L,
                "https://easypost-files.s3-us-west-2.amazonaws"
                        + ".com/files/postage_label/20200214/8240f1b66535494a82b1ec0d566c3f0f.png", "", "794685038506",
                "9405536897846100551129", "https://track.easypost.com/djE6dHJrXzY4NGJmYzU3ZjM5OTQ1Zjg5MjEzOGRmMWVmMjI1NWZl",
                null, 12L, false, "", 12L, null, 12L, "so what", "mf_testLabel", false, "shp_f470591c3fb441a68dbb9b76ecf3bb3d",
                12L, null, "44445", false, "NOT FOUND", null, null, null, null, 0L, false, "STANDALONE", null, null, null,
                null, null, null, null, null, null, BLOOD_RNA_KIT_TYPE_DISPLAY_NAME);
        Assert.assertEquals(kitWithDisplayName.getDisplayName(), BLOOD_RNA_KIT_TYPE_DISPLAY_NAME);
        Assert.assertEquals(kitWithDisplayName.getKitTypeName(), BLOOD_RNA_KIT_TYPE_NAME);

        // test that not having a display name means the display name is set as the kit type name
        KitRequestShipping kitWithoutDisplayName = new KitRequestShipping(guid, "TestProject_2", null, "FAKE_DSM_LABEL_UID", "study", BLOOD_RNA_KIT_TYPE_NAME, 1L, 1L,
                "https://easypost-files.s3-us-west-2.amazonaws"
                        + ".com/files/postage_label/20200214/8240f1b66535494a82b1ec0d566c3f0f.png", "", "794685038506",
                "9405536897846100551129", "https://track.easypost.com/djE6dHJrXzY4NGJmYzU3ZjM5OTQ1Zjg5MjEzOGRmMWVmMjI1NWZl",
                null, 12L, false, "", 12L, null, 12L, "so what", "mf_testLabel", false, "shp_f470591c3fb441a68dbb9b76ecf3bb3d",
                12L, null, "44445", false, "NOT FOUND", null, null, null, null, 0L, false, "STANDALONE", null, null, null,
                null, null, null, null, null, null, null);
        Assert.assertEquals(kitWithoutDisplayName.getDisplayName(), BLOOD_RNA_KIT_TYPE_NAME);
        Assert.assertEquals(kitWithoutDisplayName.getKitTypeName(), BLOOD_RNA_KIT_TYPE_NAME);


    }

    private static JuniperKitRequest generateKitRequestJson(){
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
                    + "\"juniperKitId\":\"kitId_\","
                    + "\"juniperParticipantID\":\"" + participantId  + "\","
                    + "\"skipAddressValidation\":false,"
                    + "\"juniperStudyID\":\"Juniper-test-guid\"}";

            return new Gson().fromJson(json, JuniperKitRequest.class);
    }
}
