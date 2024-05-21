package org.broadinstitute.dsm.kits;

import java.util.List;

import org.broadinstitute.dsm.DbTxnBaseTest;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.model.nonpepperkit.JuniperKitRequest;
import org.broadinstitute.dsm.model.nonpepperkit.NonPepperKitCreationService;
import org.broadinstitute.dsm.statics.DBConstants;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class KitDisplayNameTest extends DbTxnBaseTest {
    private static TestKitUtil testKitUtil = new TestKitUtil("kit_test_instance", "kit_test_instance_guid",
            "some_prefix", "kit_test_group", KitRequestShippingTest.BLOOD_RNA_KIT_TYPE_NAME,
            KitRequestShippingTest.BLOOD_RNA_KIT_TYPE_DISPLAY_NAME, null);

    @BeforeClass
    public static void setupBefore() {
        testKitUtil.setupInstanceAndSettings();
    }

    @AfterClass
    public static void cleanUpAfterClass() {
        testKitUtil.deleteKitsArray();
        testKitUtil.deleteGeneratedData();
    }

    @Test
    public void testKitWithDisplayName() {
        NonPepperKitCreationService nonPepperKitCreationService = new NonPepperKitCreationService();
        DDPInstance ddpInstance = DDPInstance.getDDPInstanceWithRole("kit_test_instance",
                DBConstants.JUNIPER_STUDY_INSTANCE_ROLE);
        JuniperKitRequest juniperTestKit = testKitUtil.generateKitRequestJson();
        testKitUtil.createNonPepperTestKit(juniperTestKit, nonPepperKitCreationService, ddpInstance);
        List<KitRequestShipping> kits = KitRequestShipping.getKitRequestsByRealm("kit_test_instance", "overview",
                KitRequestShippingTest.BLOOD_RNA_KIT_TYPE_NAME);
        Assert.assertEquals(1, kits.size());
        Assert.assertEquals(KitRequestShippingTest.BLOOD_RNA_KIT_TYPE_NAME, kits.get(0).getKitTypeName());
        Assert.assertEquals(KitRequestShippingTest.BLOOD_RNA_KIT_TYPE_DISPLAY_NAME, kits.get(0).getDisplayName());
        Assert.assertNotEquals(kits.get(0).getKitTypeName(), kits.get(0).getDisplayName());

    }
}
