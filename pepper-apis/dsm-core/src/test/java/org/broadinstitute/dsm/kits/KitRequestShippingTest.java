package org.broadinstitute.dsm.kits;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import liquibase.util.StringUtil;
import org.apache.commons.lang3.tuple.Pair;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.kit.KitDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.elastic.Profile;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.broadinstitute.dsm.util.TestParticipantUtil;
import org.broadinstitute.lddp.db.SimpleResult;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class KitRequestShippingTest extends DbAndElasticBaseTest {
    public static String BLOOD_RNA_KIT_TYPE_NAME = "BLOOD";
    public static String BLOOD_RNA_KIT_TYPE_DISPLAY_NAME = "BLOOD and RNA";
    private static String guid = "TEST_GUID";

    private static final String instanceName = "test_kit_request_shipping";
    private static final String shortId = "PRSTS1";
    private static final String notLegacyParticipantShortId = "PRSTS2";
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
    private static final KitDao kitDao = new KitDao();
    private static final List<Integer> dsmKitRequestIds = new ArrayList<>();
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
        for (Integer dsmKitRequestId : dsmKitRequestIds) {
            int deleteCount = kitDao.deleteKitRequestShipping(dsmKitRequestId);
            if (deleteCount != 1) {
                throw new DsmInternalError("Failed to delete kit request with id " + dsmKitRequestId);
            }
        }
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

    /**
     * Given a generated bsp sample id, parse the sample count.  The sample count
     * is the right-most int, and represents the number of samples for a given
     * kit type for a given participant.  Returns -1 if there is no kit count
     * in the sample id.
     */
    private int parseKitCountFromGeneratedSampleId(String sampleId) {
        int indexOfRightMostUnderscore = sampleId.lastIndexOf("_");
        int count = -1;
        if (indexOfRightMostUnderscore > -1) {
            String kitCount = sampleId.substring(indexOfRightMostUnderscore + 1);
            if (StringUtil.isNumeric(kitCount)) {
                count = Integer.parseInt(kitCount);
            }
        }
        return count;
    }


    /**
     * Verify that the kit-type specific kit counter suffix in the sample id takes into account
     * the legacy kits when creating a new sample id.
     */
    @Test
    public void testGenerateBspSampleIdForJuniperParticipantWithLegacyKits() {
        String shortId = "SHORT3838";
        String ddpParticipantId = "PTP3838291";
        String legacyCollaboratorParticipantId = "LEGACY_FOO_456";
        int numLegacyKits = 2;
        DDPInstance.SampleCounterOffsets originalSampleCounterOffsets = ddpInstance.getSampleCounterOffsets();
        // with legacy kits, suffix kit type count should be the number of legacy kits of that type plus one


        List<DDPInstance.SampleCounterOffsets.SampleCounterOffset> legacyKitSummaries = List.of(
                new DDPInstance.SampleCounterOffsets.SampleCounterOffset(shortId, legacyCollaboratorParticipantId,
                        Map.of(kitTestUtil.kitTypeId, numLegacyKits)));
        DDPInstance.SampleCounterOffsets sampleCounterOffsets = new DDPInstance.SampleCounterOffsets(legacyKitSummaries);
        ddpInstance.setSampleCounterOffsets(sampleCounterOffsets);

        TransactionWrapper.inTransaction(conn -> {
            try {
                String generatedSampleId = KitRequestShipping.generateBspSampleID(conn, legacyCollaboratorParticipantId, kitTestUtil.getKitTypeName(), kitTestUtil.getKitTypeId(), ddpInstance);

                int parsedSalivaKitNumberIncludingLegacyKits = parseKitCountFromGeneratedSampleId(generatedSampleId);

                // Sample names for first kits do not have a numeric suffix.  Subsequent kits do, starting at 1.
                // So the 2nd kit has a suffix of 2, the 3rd kit has a suffix of 3, etc.
                Assert.assertEquals("Unexpected kit count for " + generatedSampleId, numLegacyKits + 1, parsedSalivaKitNumberIncludingLegacyKits);

                // todo arz write a kit request the way juniper will, then verify that the
                // kit count is the legacy offset + new kit

                KitRequestShipping kitRequestShipping =  KitRequestShipping.builder()
                        .withDdpParticipantId(ddpParticipantId)
                        .withBspCollaboratorParticipantId(legacyCollaboratorParticipantId)
                        .withBspCollaboratorSampleId(generatedSampleId)
                        .withKitTypeName("SALIVA")
                        .withDdpKitRequestId(System.currentTimeMillis() + generatedSampleId)
                        .withKitTypeId(String.valueOf(kitTestUtil.kitTypeId)).build();

                String dsmKitRequestId = kitTestUtil.createKitRequestShipping(kitRequestShipping, ddpInstance, "100");
                dsmKitRequestIds.add(Integer.parseInt(dsmKitRequestId));

                String secondGeneratedSampleId = KitRequestShipping.generateBspSampleID(conn, legacyCollaboratorParticipantId, kitTestUtil.getKitTypeName(), kitTestUtil.getKitTypeId(), ddpInstance);
                int parsed2ndSalivaKitNumber = parseKitCountFromGeneratedSampleId(secondGeneratedSampleId);

                Assert.assertEquals("Unexpected kit count for " + secondGeneratedSampleId, numLegacyKits + 2, parsed2ndSalivaKitNumber);


            } finally {
                ddpInstance.setSampleCounterOffsets(originalSampleCounterOffsets);
            }
            return null;
        });

    }

    /**
     * Verify that when a ddp instance has legacy kits, the legacy participant
     * id is used.
     */
    @Test
    public void testGetCollaboratorParticipantIdWithLegacyKits() {
        DDPInstance.SampleCounterOffsets originalSampleCounterOffsets = ddpInstance.getSampleCounterOffsets();
        String shortId = "SHORT3838";
        String ddpParticipantId = "PTP3838291";
        String legacyCollaboratorParticipantId = "LEGACY_FOO_456";
        DDPInstance.SampleCounterOffsets.SampleCounterOffset legacyKits = new DDPInstance.SampleCounterOffsets.SampleCounterOffset(shortId, legacyCollaboratorParticipantId, Collections.emptyMap());

        try {
            // if there is legacy kit information, the collaborator participant id should be the collab participant id from the legacy kit data
            ddpInstance.setSampleCounterOffsets(new DDPInstance.SampleCounterOffsets(Collections.singletonList(legacyKits)));
            String collaboratorParticipantId = KitRequestShipping.getCollaboratorParticipantId(ddpInstance, ddpParticipantId, shortId, null);
            Assert.assertEquals(legacyCollaboratorParticipantId, collaboratorParticipantId);

            ddpInstance.setSampleCounterOffsets(new DDPInstance.SampleCounterOffsets(Collections.emptyList()));

            // if there's no legacy kit information, the collaborator participant id should be the [prefix]_[shortid]
            collaboratorParticipantId = KitRequestShipping.getCollaboratorParticipantId(ddpInstance, ddpParticipantId, shortId, null);
            Assert.assertEquals(collaboratorParticipantId, ddpInstance.getCollaboratorIdPrefix() + "_" + shortId);

        } finally {
            ddpInstance.setSampleCounterOffsets(originalSampleCounterOffsets);
        }
    }

    @Test
    public void testLegacyKitUpload() {
        TransactionWrapper.inTransaction(conn -> {
            String collaboratorParticipantId = "PROJ_" + shortId;
            String collaboratorSampleId = collaboratorParticipantId + "_SALIVA";

            //check when legacy participant doesn't have a prior legacy kit
            String nextCollaboratorParticipantId = KitRequestShipping.getCollaboratorParticipantId(ddpInstance,
                    legacyParticipant.getRequiredDdpParticipantId(), shortId, "0");
            String nextCollaboratorSampleId = KitRequestShipping.generateBspSampleID(conn, nextCollaboratorParticipantId, "SALIVA",
                    kitTestUtil.kitTypeId, ddpInstance);
            Assert.assertEquals(collaboratorParticipantId, nextCollaboratorParticipantId);
            Assert.assertEquals(collaboratorSampleId, nextCollaboratorSampleId);

            //now check when legacy participant has a kit with legacy id
            String legacyCollaboratorParticipantId = "PROJ_0001";
            String legacyCollaboratorSampleId =  legacyCollaboratorParticipantId + "_SALIVA";

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
                    kitTestUtil.kitTypeId, ddpInstance);

            String expectedCollaboratorSampleId =  legacyCollaboratorParticipantId + "_SALIVA_2";

            Assert.assertEquals(legacyCollaboratorParticipantId, nextCollaboratorParticipantId);
            Assert.assertEquals(expectedCollaboratorSampleId, nextCollaboratorSampleId);

            //test when Participant has both Legacy And Pepper Kit
            //this situation should not happen, but we are testing it to make sure the code handles it correctly because there might be
            // dirty data in the database

            KitRequestShipping pepperKitRequestShipping =  KitRequestShipping.builder()
                    .withDdpParticipantId(legacyParticipant.getRequiredDdpParticipantId())
                    .withBspCollaboratorParticipantId(collaboratorParticipantId)
                    .withBspCollaboratorSampleId(collaboratorSampleId + "_2")
                    .withKitTypeName("SALIVA")
                    .withDdpKitRequestId(notLegacyParticipantShortId + "_Kit2")
                    .withKitTypeId(String.valueOf(kitTestUtil.kitTypeId)).build();

            createdKits.add(kitTestUtil.createKitRequestShipping(pepperKitRequestShipping, ddpInstance, "100"));

            nextCollaboratorParticipantId = KitRequestShipping.getCollaboratorParticipantId(ddpInstance,
                    legacyParticipant.getRequiredDdpParticipantId(), shortId, "0");
            nextCollaboratorSampleId = KitRequestShipping.generateBspSampleID(conn, nextCollaboratorParticipantId, "SALIVA",
                    kitTestUtil.kitTypeId, ddpInstance);

            expectedCollaboratorSampleId =  legacyCollaboratorParticipantId + "_SALIVA_3";

            Assert.assertEquals(legacyCollaboratorParticipantId, nextCollaboratorParticipantId);
            Assert.assertEquals(expectedCollaboratorSampleId, nextCollaboratorSampleId);


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
                    kitTestUtil.kitTypeId, ddpInstance);
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
                    kitTestUtil.kitTypeId, ddpInstance);
            String expectedNextCollaboratorSampleId = "PROJ_" + notLegacyParticipantShortId + "_SALIVA_2";
            Assert.assertEquals(collaboratorParticipantId, nextCollaboratorParticipantId);
            Assert.assertEquals(expectedNextCollaboratorSampleId, nextCollaboratorSampleId);
            return null;
        });
    }

    @Test
    public void testNotHruidParticipantKitUpload() {
        //mimics when a participant is having kit creation by using legacy id or RGP subject id
        ParticipantDto notHruidParticipant;
        String notHruidParticipantGuid = "DDP_PT_ID_3";
        String notHruidId = "RGP_5883_3";
        String notHruidParticipantShortID = "PABRGP";
        Profile mimicNotHruidParticipant = new Profile();
        mimicNotHruidParticipant.setHruid(notHruidParticipantShortID);
        notHruidParticipant = TestParticipantUtil.createParticipantWithEsProfile(notHruidParticipantGuid, mimicNotHruidParticipant,
                ddpInstanceDto);
        notHruidParticipantGuid = notHruidParticipant.getRequiredDdpParticipantId();
        participants.add(notHruidParticipant);

        String collaboratorParticipantId = "PROJ_" + notHruidId;
        String collaboratorSampleId = collaboratorParticipantId + "_SALIVA";
        // We are verifying that even if a participant has an HRUID, if the RGP subject ID (notHruidId) is passed as the short ID to
        // this method,the returned collaborator participant ID and sample ID should use the subject ID (notHruidId) and not the HRUID.
        String nextCollaboratorParticipantId = KitRequestShipping.getCollaboratorParticipantId(ddpInstance,
                notHruidParticipantGuid, notHruidId, "0");
        String nextCollaboratorSampleId = (String) TransactionWrapper.inTransaction(conn -> {
            String sampleId = KitRequestShipping.generateBspSampleID(conn, nextCollaboratorParticipantId, "SALIVA",
                    kitTestUtil.kitTypeId, ddpInstance);
            return new SimpleResult(sampleId);
        }).resultValue;
        Assert.assertEquals(collaboratorParticipantId, nextCollaboratorParticipantId);
        Assert.assertEquals(collaboratorSampleId, nextCollaboratorSampleId);
    }

}
