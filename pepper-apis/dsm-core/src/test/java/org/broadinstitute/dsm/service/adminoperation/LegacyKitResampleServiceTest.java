package org.broadinstitute.dsm.service.adminoperation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.kitrequest.KitRequestDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.juniperkits.TestKitUtil;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.broadinstitute.dsm.util.MedicalRecordTestUtil;
import org.broadinstitute.dsm.util.TestParticipantUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

@Slf4j
public class LegacyKitResampleServiceTest  extends DbAndElasticBaseTest {
    private static final String instanceName = "kit_resample_service";
    private static final String shortId = "PT_SHORT";
    private static final String legacyShortId = "LEGACY_SHORT";
    private static String esIndex;
    private static DDPInstanceDto ddpInstanceDto;
    private static DDPInstanceDao ddpInstanceDao = new DDPInstanceDao();
    private static String ddpParticipantId = "PT_SAMPLE_QUEUE_TEST";
    private static ParticipantDto participantDto = null;
    private static int participantCounter = 0;
    private static Pair<ParticipantDto, String> legacyParticipantPair;
    public static TestKitUtil testKitUtil;
    private static final String newCollaboratorSampleId = "NEW_SAMPLE_ID";
    private static final String newCollaboratorParticipantId = "NEW_PARTICIPANT_ID";

    private static final String oldCollaboratorSampleId = "OLD_SAMPLE_ID";
    private static final String oldCollaboratorParticipantId = "OLD_PARTICIPANT_ID";
    private static final String ddpKitRequestId = "RESAMPLE_KIT_REQUEST_ID";
    private static List<ParticipantDto> participants = new ArrayList<>();
    private static List<String> createdKits = new ArrayList<>();

    private static LegacyKitResampleService legacyKitResampleService = new LegacyKitResampleService();
    private static DDPInstance ddpInstance;

    @BeforeClass
    public static void doFirst() {
        testKitUtil = new TestKitUtil(instanceName, instanceName, "resample", instanceName, "SALIVA", null);
        testKitUtil.setupInstanceAndSettings();
        esIndex = ElasticTestUtil.createIndex(instanceName, "elastic/lmsMappings.json", null);
        ddpInstanceDto = ddpInstanceDao.getDDPInstanceByInstanceName(instanceName).orElseThrow();
        ddpInstanceDto.setEsParticipantIndex(esIndex);
        ddpInstanceDao.updateEsParticipantIndex(ddpInstanceDto.getDdpInstanceId(), esIndex);
        ddpParticipantId = TestParticipantUtil.genDDPParticipantId(ddpParticipantId);
        legacyParticipantPair = TestParticipantUtil.createLegacyParticipant(ddpParticipantId, participantCounter++, ddpInstanceDto,
                shortId, ddpParticipantId, legacyShortId);
        participantDto = legacyParticipantPair.getLeft();
        ElasticTestUtil.createParticipant(esIndex, participantDto);
        participants.add(participantDto);
        ddpInstance = DDPInstance.getDDPInstance(instanceName);
        String dsmKitRequestId = testKitUtil.createKitRequestShipping(ddpParticipantId, oldCollaboratorSampleId,
                oldCollaboratorParticipantId, null,  ddpKitRequestId, "SALIVA", ddpInstance, "100");
        createdKits.add(dsmKitRequestId);
    }

    @AfterClass
    public static void tearDown() {
        participants.forEach(participantDto ->
                TestParticipantUtil.deleteParticipant(participantDto.getRequiredParticipantId()));
        createdKits.forEach(kitRequestId -> testKitUtil.deleteKitRequestShipping(Integer.parseInt(kitRequestId)));
        testKitUtil.deleteGeneratedData();
        ElasticTestUtil.deleteIndex(esIndex);
    }

    @After
    public void deleteParticipantData() {
        MedicalRecordTestUtil.deleteInstanceMedicalRecordBundles(ddpInstanceDto);
        TestParticipantUtil.deleteInstanceParticipants(ddpInstanceDto);
    }

    @Test
    public void testVerify_notVerifyMissingOldCollaboratorSampleId() {
        LegacyKitResampleRequest wrongKitResampleRequest = new LegacyKitResampleRequest(null,
                newCollaboratorSampleId, newCollaboratorParticipantId, shortId, legacyShortId);
        try {
            wrongKitResampleRequest.verify(ddpInstance, new KitRequestDao());
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertEquals("Missing required field: currentCollaboratorSampleId", e.getMessage());
            return;
        }
        Assert.fail("Should have thrown exception");
    }

    @Test
    public void testVerify_notVerifyMissingNewCollaboratorSampleId() {
        LegacyKitResampleRequest wrongKitResampleRequest = new LegacyKitResampleRequest(oldCollaboratorSampleId,
                null, newCollaboratorParticipantId, shortId, legacyShortId);
        try {
            wrongKitResampleRequest.verify(ddpInstance, new KitRequestDao());
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertEquals("Missing required field: newCollaboratorSampleId", e.getMessage());
            return;
        }
        Assert.fail("Should have thrown exception");
    }

    @Test
    public void testVerify_notVerifyMissingShortId() {
        LegacyKitResampleRequest wrongKitResampleRequest = new LegacyKitResampleRequest(oldCollaboratorSampleId,
                newCollaboratorSampleId, newCollaboratorParticipantId, null, legacyShortId);
        try {
            wrongKitResampleRequest.verify(ddpInstance, new KitRequestDao());
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertEquals("Missing required field: shortId", e.getMessage());
            return;
        }
        Assert.fail("Should have thrown exception");
    }

    @Test
    public void testVerify_notVerifyMissingNewCollaboratorParticipantId() {
        LegacyKitResampleRequest wrongKitResampleRequest = new LegacyKitResampleRequest(oldCollaboratorSampleId,
                newCollaboratorSampleId, null, shortId, legacyShortId);
        try {
            wrongKitResampleRequest.verify(ddpInstance, new KitRequestDao());
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertEquals("Missing required field: newCollaboratorParticipantId", e.getMessage());
            return;
        }
        Assert.fail("Should have thrown exception");
    }

    @Test
    public void testVerify_notVerifyWrongShortId() {
        String wrongShortId = "WRONG_SHORT_ID";
        LegacyKitResampleRequest wrongParticipantLegacyKitResampleRequest = new LegacyKitResampleRequest(oldCollaboratorSampleId,
                newCollaboratorSampleId, newCollaboratorParticipantId, wrongShortId, legacyShortId);
        try {
            wrongParticipantLegacyKitResampleRequest.verify(ddpInstance, new KitRequestDao());
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertEquals("Invalid participant short ID %s".formatted(wrongShortId), e.getMessage());
            return;
        }
    }

    @Test
    public void testVerify_notVerifyWrongLegacyShortId() {
        String wrongLegacyShortId = "WRONG_LEGACY_SHORT_ID";
        LegacyKitResampleRequest wrongLegacyIdLegacyKitResampleRequest = new LegacyKitResampleRequest(oldCollaboratorSampleId,
                newCollaboratorSampleId, newCollaboratorParticipantId, shortId, wrongLegacyShortId);

        try {
            wrongLegacyIdLegacyKitResampleRequest.verify(ddpInstance, new KitRequestDao());
        } catch (Exception e2) {
            e2.printStackTrace();
            Assert.assertEquals(("Legacy short ID %s does not match legacy short ID on file %s for participant short ID %s, "
                    + " will not resample kit %s")
                    .formatted(wrongLegacyShortId, legacyShortId, shortId, oldCollaboratorSampleId), e2.getMessage());
            return;
        }
        Assert.fail("Should have thrown exception");
    }

    @Test
    public void testVerify_notVerifyDuplicateCollaboratorSampleID() {
        String collaboratorSampleId = "DUP_COLLABORATOR_SAMPLE_ID";
        String dsmKitRequestId = testKitUtil.createKitRequestShipping(ddpParticipantId, collaboratorSampleId,
                oldCollaboratorParticipantId, null,  "NEW_DDP_KIT", "SALIVA", ddpInstance, "100");
        createdKits.add(dsmKitRequestId);
        LegacyKitResampleRequest duplicateSampleIdRequest = new LegacyKitResampleRequest(oldCollaboratorSampleId,
                collaboratorSampleId, newCollaboratorParticipantId, shortId, legacyShortId);
        try {
            duplicateSampleIdRequest.verify(ddpInstance, new KitRequestDao());
        } catch (Exception e2) {
            e2.printStackTrace();
            Assert.assertEquals(("Kit request with the new collaboratorSampleId %s already exists!").formatted(collaboratorSampleId),
                    e2.getMessage());
            return;
        }
        Assert.fail("Should have thrown exception");
    }

    @Test
    public void testVerifyAndResample() {
        LegacyKitResampleRequest legacyKitResampleRequest = new LegacyKitResampleRequest(oldCollaboratorSampleId, newCollaboratorSampleId,
                newCollaboratorParticipantId, shortId, legacyShortId);
        try {
            legacyKitResampleRequest.verify(ddpInstance, new KitRequestDao());
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Should not have thrown exception");
        }
        LegacyKitResampleList legacyKitResampleList = new LegacyKitResampleList();
        legacyKitResampleList.setResampleRequestList(List.of(legacyKitResampleRequest));
        legacyKitResampleService.setLegacyKitResampleList(legacyKitResampleList);
        legacyKitResampleService.setDdpInstance(ddpInstance);
        legacyKitResampleService.run(1);
        KitRequestShipping kitRequestShipping = KitRequestShipping.getKitRequest(Integer.parseInt(createdKits.get(0)));
        Assert.assertEquals(newCollaboratorSampleId, kitRequestShipping.getBspCollaboratorSampleId());
        Assert.assertEquals(newCollaboratorParticipantId, kitRequestShipping.getBspCollaboratorParticipantId());
        Assert.assertEquals(legacyParticipantPair.getRight(), kitRequestShipping.getDdpParticipantId());
        Map<String, Object> participantDsm = ElasticSearchUtil.getDsmForSingleParticipantFromES(ddpInstance.getName(),
                ddpInstance.getParticipantIndexES(), legacyKitResampleRequest.getShortId());
        List<Map<String, Object>> kitRequests = (List<Map<String, Object>>) participantDsm.get("kitRequestShipping");
        Assert.assertEquals(1, kitRequests.size());
        Assert.assertEquals(newCollaboratorSampleId, kitRequests.get(0).get("bspCollaboratorSampleId"));
        Assert.assertEquals(newCollaboratorParticipantId, kitRequests.get(0).get("bspCollaboratorParticipantId"));
        Assert.assertEquals(legacyParticipantPair.getRight(), kitRequests.get(0).get("ddpParticipantId"));
    }

}
