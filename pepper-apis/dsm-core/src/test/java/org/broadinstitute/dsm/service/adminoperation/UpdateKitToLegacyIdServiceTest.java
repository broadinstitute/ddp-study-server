package org.broadinstitute.dsm.service.adminoperation;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.kits.TestKitUtil;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.service.elastic.ElasticSearchService;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.broadinstitute.dsm.util.TestParticipantUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

@Slf4j
public class UpdateKitToLegacyIdServiceTest extends DbAndElasticBaseTest {
    private static final String instanceName = "kit_update_collab_id";
    private static final String shortId = "PT_SHORT";
    private static final String legacyShortId = "LEGACY_SHORT";
    private static String esIndex;
    private static DDPInstanceDto ddpInstanceDto;
    private static DDPInstanceDao ddpInstanceDao = new DDPInstanceDao();
    private static String ddpParticipantId = "PT_SAMPLE_QUEUE_TEST";
    private static int participantCounter = 0;
    private static Pair<ParticipantDto, String> legacyParticipantPair;
    private static TestKitUtil testKitUtil;
    private static final String newCollaboratorSampleId = "NEW_SAMPLE_ID";
    private static final String newCollaboratorParticipantId = "NEW_PARTICIPANT_ID";

    private static final String oldCollaboratorSampleId = "OLD_SAMPLE_ID";
    private static final String oldCollaboratorParticipantId = "OLD_PARTICIPANT_ID";
    private static final String ddpKitRequestId = "Update_Collab_KIT_REQUEST_ID";
    private static List<ParticipantDto> participants = new ArrayList<>();
    private static List<String> createdKits = new ArrayList<>();
    private ElasticSearchService elasticSearchService = new ElasticSearchService();

    @BeforeClass
    public static void doFirst() {
        esIndex = ElasticTestUtil.createIndex(instanceName, "elastic/lmsMappings.json", null);
        testKitUtil = new TestKitUtil(instanceName, instanceName, "UpdateCollab", instanceName, "SALIVA", null, esIndex);
        testKitUtil.setupInstanceAndSettings();
        ddpInstanceDto = ddpInstanceDao.getDDPInstanceByInstanceName(instanceName).orElseThrow();
        legacyParticipantPair = TestParticipantUtil.createLegacyParticipant(ddpParticipantId, participantCounter++, ddpInstanceDto,
                shortId, legacyShortId);
        participants.add(legacyParticipantPair.getLeft());
        KitRequestShipping kitRequestShipping =  KitRequestShipping.builder()
                .withDdpParticipantId(ddpParticipantId)
                .withBspCollaboratorSampleId(oldCollaboratorSampleId)
                .withBspCollaboratorParticipantId(oldCollaboratorParticipantId)
                .withDdpKitRequestId(ddpKitRequestId)
                .withKitTypeName("SALIVA")
                .withKitTypeId(String.valueOf(testKitUtil.kitTypeId)).build();

        String dsmKitRequestId = testKitUtil.createKitRequestShipping(kitRequestShipping, DDPInstance.from(ddpInstanceDto), "100");
        createdKits.add(dsmKitRequestId);
    }

    @AfterClass
    public static void tearDown() {
        participants.forEach(participantDto ->
                TestParticipantUtil.deleteParticipant(participantDto.getRequiredParticipantId()));
        createdKits.forEach(dsmKitRequestId -> testKitUtil.deleteKitRequestShipping((Integer.parseInt(dsmKitRequestId))));
        testKitUtil.deleteGeneratedData();
        ddpInstanceDao.delete(ddpInstanceDto.getDdpInstanceId());
        ElasticTestUtil.deleteIndex(esIndex);
    }

    @Test
    public void test_notUpdateWhenWrongShortId() {
        String wrongShortId = "WRONG_SHORT_ID";
        UpdateKitToLegacyIdsRequest wrongShortIdReq = new UpdateKitToLegacyIdsRequest(
                oldCollaboratorSampleId, newCollaboratorSampleId, newCollaboratorParticipantId, wrongShortId, legacyShortId);
        UpdateLog updateLog = UpdateKitToLegacyIdService.changeKitIdsToLegacyIds(wrongShortIdReq, ddpInstanceDto);

        log.debug(updateLog.getMessage());
        Assert.assertEquals(UpdateLog.UpdateStatus.ERROR, updateLog.getStatus());
        Assert.assertEquals("Participant not found for short id WRONG_SHORT_ID", updateLog.getMessage());
    }

    @Test
    public void test_notUpdateWhenWrongLegacyShortId() {
        String wrongLegacyShortId = "WRONG_LEGACY_SHORT_ID";
        UpdateKitToLegacyIdsRequest wrongLegacyIdReq = new UpdateKitToLegacyIdsRequest(
                oldCollaboratorSampleId, newCollaboratorSampleId, newCollaboratorParticipantId, shortId, wrongLegacyShortId);
        UpdateLog updateLog = UpdateKitToLegacyIdService.changeKitIdsToLegacyIds(wrongLegacyIdReq, ddpInstanceDto);
        log.debug(updateLog.getMessage());
        Assert.assertEquals(UpdateLog.UpdateStatus.ERROR, updateLog.getStatus());
        Assert.assertEquals(("Legacy short ID %s does not match legacy short ID on file %s for participant short ID %s, "
                        + " will not update kit %s")
                .formatted(wrongLegacyShortId, legacyShortId, shortId, oldCollaboratorSampleId), updateLog.getMessage());
    }

    @Test
    public void test_notUpdateWhenDuplicateCollaboratorSampleID() {
        String collaboratorSampleId = "NEW_COLLABORATOR_SAMPLE_ID";
        String collaboratorParticipantId = "SOME_COLLAB_PARTICIPANT_ID";
        KitRequestShipping kitRequestShipping =  KitRequestShipping.builder()
                .withDdpParticipantId(ddpParticipantId)
                .withBspCollaboratorSampleId(collaboratorSampleId)
                .withBspCollaboratorParticipantId(collaboratorParticipantId)
                .withDdpKitRequestId("KIT_REQ_ID_DUP_KIT1")
                .withKitTypeName("SALIVA")
                .withKitTypeId(String.valueOf(testKitUtil.kitTypeId)).build();
        String dsmKitRequestId = testKitUtil.createKitRequestShipping(kitRequestShipping, DDPInstance.from(ddpInstanceDto), "100");
        createdKits.add(dsmKitRequestId);
        String dupCollaboratorSampleId = "DUP_COLLABORATOR_SAMPLE_ID";
        KitRequestShipping kitRequestShipping2 =  KitRequestShipping.builder()
                .withDdpParticipantId(ddpParticipantId)
                .withBspCollaboratorSampleId(dupCollaboratorSampleId)
                .withBspCollaboratorParticipantId(collaboratorParticipantId)
                .withDdpKitRequestId("KIT_REQ_ID_DUP_KIT2")
                .withKitTypeName("SALIVA")
                .withKitTypeId(String.valueOf(testKitUtil.kitTypeId)).build();
        String dsmKitRequestId2 = testKitUtil.createKitRequestShipping(kitRequestShipping2, DDPInstance.from(ddpInstanceDto), "100");
        createdKits.add(dsmKitRequestId2);

        UpdateKitToLegacyIdsRequest duplicateSampleIdRequest = new UpdateKitToLegacyIdsRequest(collaboratorSampleId,
                dupCollaboratorSampleId, collaboratorParticipantId, shortId, legacyShortId);
        UpdateLog updateLog = UpdateKitToLegacyIdService.changeKitIdsToLegacyIds(duplicateSampleIdRequest, ddpInstanceDto);
        log.debug(updateLog.getMessage());
        Assert.assertEquals(UpdateLog.UpdateStatus.ERROR, updateLog.getStatus());
        Assert.assertEquals("Kit request with the new collaboratorSampleId DUP_COLLABORATOR_SAMPLE_ID already exists!",
                updateLog.getMessage());
    }

    @Test
    public void testVerifyAndUpdateCollab() {
        UpdateKitToLegacyIdsRequest
                updateKitToLegacyIdsRequest = new UpdateKitToLegacyIdsRequest(oldCollaboratorSampleId, newCollaboratorSampleId,
                newCollaboratorParticipantId, shortId, legacyShortId);

        UpdateLog updateLog = UpdateKitToLegacyIdService.changeKitIdsToLegacyIds(updateKitToLegacyIdsRequest, ddpInstanceDto);
        log.debug(updateLog.getMessage());
        Assert.assertEquals(UpdateLog.UpdateStatus.ES_UPDATED, updateLog.getStatus());

        KitRequestShipping kitRequestShipping = KitRequestShipping.getKitRequest(Integer.parseInt(createdKits.get(0)));
        Assert.assertEquals(newCollaboratorSampleId, kitRequestShipping.getBspCollaboratorSampleId());
        Assert.assertEquals(newCollaboratorParticipantId, kitRequestShipping.getBspCollaboratorParticipantId());
        Assert.assertEquals(legacyParticipantPair.getRight(), kitRequestShipping.getDdpParticipantId());
        ElasticSearchParticipantDto esParticipant =
                elasticSearchService.getRequiredParticipantDocument(ddpParticipantId, esIndex);
        Dsm participantDsm = esParticipant.getDsm().orElseThrow();
        List<KitRequestShipping> kitRequests = participantDsm.getKitRequestShipping();
        kitRequests.stream().filter(kitRequest -> kitRequest.getBspCollaboratorSampleId().equals(oldCollaboratorSampleId))
                .findFirst().ifPresent(kitRequest -> Assert.fail("Old collaborator sample id should not be present"));
        kitRequests.stream().filter(kitRequest -> kitRequest.getBspCollaboratorParticipantId()
                        .equals(oldCollaboratorParticipantId)).findAny()
                .ifPresent(kitRequest -> Assert.fail("Old collaborator participant id should not be present"));
        Assert.assertTrue(kitRequests.stream().anyMatch(kitRequest -> kitRequest.getBspCollaboratorSampleId()
                .equals(newCollaboratorSampleId)));
        Assert.assertTrue(kitRequests.stream().anyMatch(kitRequest -> kitRequest.getBspCollaboratorParticipantId()
                .equals(newCollaboratorParticipantId)));
        Assert.assertTrue(kitRequests.stream().anyMatch(kitRequest ->
                legacyParticipantPair.getRight().equals(kitRequest.getDdpParticipantId())));
    }

}
