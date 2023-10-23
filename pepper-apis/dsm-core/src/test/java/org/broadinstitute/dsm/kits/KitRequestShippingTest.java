package org.broadinstitute.dsm.kits;

import org.broadinstitute.dsm.db.KitRequestShipping;
import org.junit.Assert;
import org.junit.Test;

public class KitRequestShippingTest {
    private static String BLOOD_RNA_KIT_TYPE_NAME = "BLOOD";
    private static String BLOOD_RNA_KIT_TYPE_DISPLAY_NAME = "BLOOD and RNA";
    private static String guid = "TEST_GUID";



    @Test
    public void testEmptyDisplayName() {
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



    @Test
    public void testNotEmptyDisplayName() {
        // test that  having a display name means the display name is different from the kit type name
        KitRequestShipping kitWithDisplayName = new KitRequestShipping(guid, "TestProject_2", null, "FAKE_DSM_LABEL_UID", "study", BLOOD_RNA_KIT_TYPE_NAME, 1L, 1L,
                "https://easypost-files.s3-us-west-2.amazonaws"
                        + ".com/files/postage_label/20200214/8240f1b66535494a82b1ec0d566c3f0f.png", "", "794685038506",
                "9405536897846100551129", "https://track.easypost.com/djE6dHJrXzY4NGJmYzU3ZjM5OTQ1Zjg5MjEzOGRmMWVmMjI1NWZl",
                null, 12L, false, "", 12L, null, 12L, "so what", "mf_testLabel", false, "shp_f470591c3fb441a68dbb9b76ecf3bb3d",
                12L, null, "44445", false, "NOT FOUND", null, null, null, null, 0L, false, "STANDALONE", null, null, null,
                null, null, null, null, null, null, BLOOD_RNA_KIT_TYPE_DISPLAY_NAME);
        Assert.assertEquals(kitWithDisplayName.getDisplayName(), BLOOD_RNA_KIT_TYPE_DISPLAY_NAME);
        Assert.assertEquals(kitWithDisplayName.getKitTypeName(), BLOOD_RNA_KIT_TYPE_NAME);
    }

}
