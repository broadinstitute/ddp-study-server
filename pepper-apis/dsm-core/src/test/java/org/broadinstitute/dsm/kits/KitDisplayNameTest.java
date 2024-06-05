package org.broadinstitute.dsm.kits;

import java.util.List;

import org.broadinstitute.dsm.DbTxnBaseTest;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDao;
import org.broadinstitute.dsm.db.dao.kit.KitDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.util.KitShippingTestUtil;
import org.broadinstitute.dsm.util.TestParticipantUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class KitDisplayNameTest extends DbTxnBaseTest {
    private static final String instanceName = "kit_test_instance";
    private static KitTestUtil kitTestUtil = new KitTestUtil(instanceName, instanceName,
            "some_prefix", "kit_test_group", KitRequestShippingTest.BLOOD_RNA_KIT_TYPE_NAME,
            KitRequestShippingTest.BLOOD_RNA_KIT_TYPE_DISPLAY_NAME, null, false);

    private static KitShippingTestUtil kitShippingTestUtil;
    private static DDPInstanceDto ddpInstanceDto;
    private static DDPInstanceDao ddpInstanceDao = new DDPInstanceDao();
    private static String ddpParticipantId;
    private static ParticipantDto participantDto;
    private static ParticipantDao participantDao = new ParticipantDao();
    private static final String TEST_USER = "kitDisplayNameTestUser";

    @BeforeClass
    public static void setupBefore() {
        kitTestUtil.setupInstanceAndSettings();
        kitShippingTestUtil = new KitShippingTestUtil(TEST_USER, "kitDisplayNameTest");
        ddpInstanceDto = ddpInstanceDao.getDDPInstanceByInstanceId(kitTestUtil.getDdpInstanceId()).orElseThrow();
        ddpParticipantId = TestParticipantUtil.genDDPParticipantId("kitDisplayNameTest");
        participantDto = TestParticipantUtil.createParticipant(ddpParticipantId, ddpInstanceDto.getDdpInstanceId());
    }

    @AfterClass
    public static void cleanUpAfterClass() {
        kitShippingTestUtil.tearDown();
        participantDao.delete(participantDto.getParticipantId().get());
        kitTestUtil.deleteGeneratedData();
    }

    @Test
    public void testKitWithDisplayName() {
        int kitRequestId = kitShippingTestUtil.createTestKitShippingWithKitType(participantDto, ddpInstanceDto,
                KitRequestShippingTest.BLOOD_RNA_KIT_TYPE_NAME, kitTestUtil.getKitTypeId(), false);
        KitRequestShipping kitRequestShipping = KitDao.getKitRequest(kitRequestId).orElseThrow();
        List<KitRequestShipping> kits = KitRequestShipping.getKitRequestsByRealm(instanceName, KitRequestShipping.OVERVIEW,
                KitRequestShippingTest.BLOOD_RNA_KIT_TYPE_NAME);
        Assert.assertEquals(1, kits.size());
        Assert.assertEquals(KitRequestShippingTest.BLOOD_RNA_KIT_TYPE_NAME, kits.get(0).getKitTypeName());
        Assert.assertEquals(KitRequestShippingTest.BLOOD_RNA_KIT_TYPE_DISPLAY_NAME, kits.get(0).getDisplayName());
        Assert.assertNotEquals(kits.get(0).getKitTypeName(), kits.get(0).getDisplayName());

    }
}
