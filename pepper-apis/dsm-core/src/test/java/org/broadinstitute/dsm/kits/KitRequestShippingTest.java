package org.broadinstitute.dsm.kits;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.model.elastic.Profile;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.broadinstitute.dsm.util.TestParticipantUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class KitRequestShippingTest extends DbAndElasticBaseTest {
    public static String BLOOD_RNA_KIT_TYPE_NAME = "BLOOD";
    public static String BLOOD_RNA_KIT_TYPE_DISPLAY_NAME = "BLOOD and RNA";
    private static String guid = "TEST_GUID";

    private static final String instanceName = "test_kit_request_shipping";
    private static final String shortId = "KRSTS1";
    private static final String notLegacyParticipantShortId = "KRSTS2";
    private static final String legacyShortId = "0001";
    private static final String collaboratorIdPrefix = "PROJ";
    private static String esIndex;
    private static DDPInstanceDto ddpInstanceDto;
    private static DDPInstance ddpInstance;
    private static DDPInstanceDao ddpInstanceDao = new DDPInstanceDao();
    private static String legacyParticipantGuid = "DDP_PT_ID_1";
    private static String legacyAltpid;
    private static ParticipantDto legacyParticipant;
    private static String notLegacyParticipantGuid = "DDP_PT_ID_2";
    private static ParticipantDto notLegacyParticipant;
    private static int participantCounter = 0;
    private static Pair<ParticipantDto, String> legacyParticipantPair;
    private static KitTestUtil kitTestUtil;
    private static List<ParticipantDto> participants = new ArrayList<>();
    private static List<String> createdKits = new ArrayList<>();

    @BeforeClass
    public static void doFirst() {
        esIndex = ElasticTestUtil.createIndex(instanceName, "elastic/lmsMappings.json", null);
        kitTestUtil = new KitTestUtil(instanceName, instanceName, collaboratorIdPrefix, instanceName, "SALIVA", null, esIndex, false);
        kitTestUtil.setupInstanceAndSettings();
        ddpInstanceDao.setMigratedDdp(kitTestUtil.ddpInstanceId, true);
        ddpInstanceDto = ddpInstanceDao.getDDPInstanceByInstanceName(instanceName).orElseThrow();
        Assert.assertTrue(ddpInstanceDto.getMigratedDdp());
        ddpInstance = DDPInstance.from(ddpInstanceDto);
        legacyParticipantPair = TestParticipantUtil.createLegacyParticipant(legacyParticipantGuid, participantCounter++, ddpInstanceDto,
                shortId, legacyShortId);

        legacyParticipant = legacyParticipantPair.getLeft();
        participants.add(legacyParticipant);
        legacyParticipantGuid = legacyParticipant.getRequiredDdpParticipantId();
        legacyAltpid = legacyParticipantPair.getRight();

        Profile profile = new Profile();
        profile.setHruid(notLegacyParticipantShortId);
        notLegacyParticipant = TestParticipantUtil.createParticipantWithEsProfile(notLegacyParticipantGuid, profile, ddpInstanceDto);
        notLegacyParticipantGuid = notLegacyParticipant.getRequiredDdpParticipantId();
        participants.add(notLegacyParticipant);
    }

    @AfterClass
    public static void tearDown() {
        participants.forEach(participantDto ->
                TestParticipantUtil.deleteParticipant(participantDto.getRequiredParticipantId()));
        createdKits.forEach(dsmKitRequestId -> kitTestUtil.deleteKitRequestShipping((Integer.parseInt(dsmKitRequestId))));
        kitTestUtil.deleteGeneratedData();
        ddpInstanceDao.delete(ddpInstanceDto.getDdpInstanceId());
        ElasticTestUtil.deleteIndex(esIndex);
    }

    @Test
    public void testEmptyDisplayName() {
        // test that not having a display name means the display name is set as the kit type name
        KitRequestShipping kitWithoutDisplayName = new KitRequestShipping(guid, "TestProject_2", null, "FAKE_DSM_LABEL_UID", "study",
                BLOOD_RNA_KIT_TYPE_NAME, 1, 1L,
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
        KitRequestShipping kitWithDisplayName = new KitRequestShipping(guid, "TestProject_2", null, "FAKE_DSM_LABEL_UID", "study",
                BLOOD_RNA_KIT_TYPE_NAME, 1, 1L,
                "https://easypost-files.s3-us-west-2.amazonaws"
                        + ".com/files/postage_label/20200214/8240f1b66535494a82b1ec0d566c3f0f.png", "", "794685038506",
                "9405536897846100551129", "https://track.easypost.com/djE6dHJrXzY4NGJmYzU3ZjM5OTQ1Zjg5MjEzOGRmMWVmMjI1NWZl",
                null, 12L, false, "", 12L, null, 12L, "so what", "mf_testLabel", false, "shp_f470591c3fb441a68dbb9b76ecf3bb3d",
                12L, null, "44445", false, "NOT FOUND", null, null, null, null, 0L, false, "STANDALONE", null, null, null,
                null, null, null, null, null, null, BLOOD_RNA_KIT_TYPE_DISPLAY_NAME);
        Assert.assertEquals(kitWithDisplayName.getDisplayName(), BLOOD_RNA_KIT_TYPE_DISPLAY_NAME);
        Assert.assertEquals(kitWithDisplayName.getKitTypeName(), BLOOD_RNA_KIT_TYPE_NAME);
    }

    @Test
    public void testHasBSPCollaboratorParticipantId() {
        KitRequestShipping shipping = new KitRequestShipping();
        shipping.setBspCollaboratorParticipantId("123");
        Assert.assertTrue(shipping.hasBSPCollaboratorParticipantId());

        shipping.setBspCollaboratorParticipantId("");
        Assert.assertFalse(shipping.hasBSPCollaboratorParticipantId());

        shipping.setBspCollaboratorParticipantId(null);
        Assert.assertFalse(shipping.hasBSPCollaboratorParticipantId());
    }

    @Test
    public void testLegacyKitUpload() {
        TransactionWrapper.inTransaction(conn -> {
            String collaboratorParticipantId = "PROJ_" + shortId;
            String collaboratorSampleId = collaboratorParticipantId + "_SALIVA";
            String legacyCollaboratorParticipantId = "PROJ_0001";
            String legacyCollaboratorSampleId =  legacyCollaboratorParticipantId + "_SALIVA";
            String expectedNextCollaboratorSampleId = legacyCollaboratorSampleId + "_2";

            //check when legacy participant doesn't have a prior legacy kit
            String nextCollaboratorParticipantId = KitRequestShipping.getCollaboratorParticipantId(ddpInstance,
                    legacyParticipant.getRequiredDdpParticipantId(), shortId, "0");
            String nextCollaboratorSampleId = KitRequestShipping.generateBspSampleID(conn, nextCollaboratorParticipantId, "SALIVA",
                    kitTestUtil.kitTypeId);
            Assert.assertEquals(collaboratorParticipantId, nextCollaboratorParticipantId);
            Assert.assertEquals(collaboratorSampleId, nextCollaboratorSampleId);

            //now check when legacy participant has a kit with legacy id and legacy altpid
            KitRequestShipping kitRequestShipping =  KitRequestShipping.builder()
                    .withDdpParticipantId(legacyAltpid)
                    .withBspCollaboratorParticipantId(legacyCollaboratorParticipantId)
                    .withBspCollaboratorSampleId(legacyCollaboratorSampleId)
                    .withKitTypeName("SALIVA")
                    .withDdpKitRequestId("0001_Kit")
                    .withKitTypeId(String.valueOf(kitTestUtil.kitTypeId)).build();

            String dsmKitRequestId = kitTestUtil.createKitRequestShipping(kitRequestShipping, ddpInstance, "100");
            createdKits.add(dsmKitRequestId);

            nextCollaboratorParticipantId = KitRequestShipping.getCollaboratorParticipantId(ddpInstance,
                    legacyParticipant.getRequiredDdpParticipantId(), shortId, "0");
            nextCollaboratorSampleId = KitRequestShipping.generateBspSampleID(conn, nextCollaboratorParticipantId, "SALIVA",
                    kitTestUtil.kitTypeId);

            Assert.assertEquals(legacyCollaboratorParticipantId, nextCollaboratorParticipantId);
            Assert.assertEquals(expectedNextCollaboratorSampleId, nextCollaboratorSampleId);

            return null;
        });
    }

    @Test
    public void testPepperParticipantKitUpload() {
        TransactionWrapper.inTransaction(conn -> {
            String collaboratorParticipantId = "PROJ_" + notLegacyParticipantShortId;
            String collaboratorSampleId = "PROJ_" + notLegacyParticipantShortId + "_SALIVA";
            //check when legacy participant doesn't have a prior legacy kit
            String nextCollaboratorParticipantId = KitRequestShipping.getCollaboratorParticipantId(ddpInstance,
                    notLegacyParticipantGuid, notLegacyParticipantShortId, "0");
            String nextCollaboratorSampleId = KitRequestShipping.generateBspSampleID(conn, nextCollaboratorParticipantId, "SALIVA",
                    kitTestUtil.kitTypeId);
            Assert.assertEquals(collaboratorParticipantId, nextCollaboratorParticipantId);
            Assert.assertEquals(collaboratorSampleId, nextCollaboratorSampleId);

            //now check when pepper participant has a kit without legacy id
            KitRequestShipping kitRequestShipping =  KitRequestShipping.builder()
                    .withDdpParticipantId(legacyParticipant.getRequiredDdpParticipantId())
                    .withBspCollaboratorParticipantId(collaboratorParticipantId)
                    .withBspCollaboratorSampleId(collaboratorSampleId)
                    .withKitTypeName("SALIVA")
                    .withDdpKitRequestId(notLegacyParticipantShortId + "_Kit")
                    .withKitTypeId(String.valueOf(kitTestUtil.kitTypeId)).build();

            String dsmKitRequestId = kitTestUtil.createKitRequestShipping(kitRequestShipping, ddpInstance, "100");
            createdKits.add(dsmKitRequestId);
            nextCollaboratorParticipantId = KitRequestShipping.getCollaboratorParticipantId(ddpInstance,
                    notLegacyParticipantGuid, notLegacyParticipantShortId, "0");
            nextCollaboratorSampleId = KitRequestShipping.generateBspSampleID(conn, nextCollaboratorParticipantId, "SALIVA",
                    kitTestUtil.kitTypeId);
            String expectedNextCollaboratorSampleId = "PROJ_" + notLegacyParticipantShortId + "_SALIVA_2";
            Assert.assertEquals(collaboratorParticipantId, nextCollaboratorParticipantId);
            Assert.assertEquals(expectedNextCollaboratorSampleId, nextCollaboratorSampleId);
            return null;
        });
    }

}
