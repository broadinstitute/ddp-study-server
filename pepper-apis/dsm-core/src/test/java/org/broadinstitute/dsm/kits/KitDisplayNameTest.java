package org.broadinstitute.dsm.kits;

import java.util.List;

import org.broadinstitute.dsm.DbTxnBaseTest;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.juniperkits.TestKitUtil;
import org.broadinstitute.dsm.model.nonpepperkit.JuniperKitRequest;
import org.broadinstitute.dsm.model.nonpepperkit.NonPepperKitCreationService;
import org.broadinstitute.dsm.statics.DBConstants;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class KitDisplayNameTest extends DbTxnBaseTest {
    private static String BLOOD_RNA_KIT_TYPE_NAME = "BLOOD";
    private static String BLOOD_RNA_KIT_TYPE_DISPLAY_NAME = "BLOOD and RNA";
    private static TestKitUtil
            testKitUtil = new TestKitUtil("test_instance", "test_instance_guid", "some_prefix", "test_group", BLOOD_RNA_KIT_TYPE_NAME);

    @BeforeClass
    public static void setupBefore() {
        testKitUtil.setupInstanceAndSettings();
    }

    @AfterClass
    public static void cleanUpAfterClass() {
        testKitUtil.deleteKitsArray();
        testKitUtil.deleteInstanceAndSettings();
    }

    @Test
    public void testKitWithDisplayName(){
        NonPepperKitCreationService nonPepperKitCreationService = new NonPepperKitCreationService();
        DDPInstance ddpInstance = DDPInstance.getDDPInstanceWithRole("test_instance", DBConstants.JUNIPER_STUDY_INSTANCE_ROLE);
        JuniperKitRequest juniperTestKit = TestKitUtil.generateKitRequestJson();
        TestKitUtil.createNonPepperTestKit(juniperTestKit, nonPepperKitCreationService, ddpInstance);
        List<KitRequestShipping> kits = KitRequestShipping.getKitRequestsByRealm("test_instance", "overview", BLOOD_RNA_KIT_TYPE_NAME);
        Assert.assertEquals(1, kits.size());
        Assert.assertEquals(BLOOD_RNA_KIT_TYPE_NAME, kits.get(0).getKitTypeName());
        Assert.assertEquals(BLOOD_RNA_KIT_TYPE_DISPLAY_NAME, kits.get(0).getDisplayName());
        Assert.assertNotEquals(kits.get(0).getKitTypeName(), kits.get(0).getDisplayName());

    }
}
