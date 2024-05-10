package org.broadinstitute.dsm.service.adminoperation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.kitrequest.KitRequestDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.kits.TestKitUtil;
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
    public static TestKitUtil testKitUtil;
    private static final String newCollaboratorSampleId = "NEW_SAMPLE_ID";
    private static final String newCollaboratorParticipantId = "NEW_PARTICIPANT_ID";

    private static final String oldCollaboratorSampleId = "OLD_SAMPLE_ID";
    private static final String oldCollaboratorParticipantId = "OLD_PARTICIPANT_ID";
    private static final String ddpKitRequestId = "Update_Collab_KIT_REQUEST_ID";
    private static List<ParticipantDto> participants = new ArrayList<>();
    private static List<String> createdKits = new ArrayList<>();

    private static UpdateKitToLegacyIdService updateKitToLegacyIdService = new UpdateKitToLegacyIdService();

    @BeforeClass
    public static void doFirst() {
        esIndex = ElasticTestUtil.createIndex(instanceName, "elastic/lmsMappings.json", null);
        testKitUtil = new TestKitUtil(instanceName, instanceName, "UpdateCollab", instanceName, "SALIVA", null, esIndex);
        testKitUtil.setupInstanceAndSettings();
        ddpInstanceDto = ddpInstanceDao.getDDPInstanceByInstanceName(instanceName).orElseThrow();
        legacyParticipantPair = TestParticipantUtil.createLegacyParticipant(ddpParticipantId, participantCounter++, ddpInstanceDto,
                shortId, legacyShortId);
        participants.add(legacyParticipantPair.getLeft());
        String dsmKitRequestId = testKitUtil.createKitRequestShipping(ddpParticipantId, oldCollaboratorSampleId,
                oldCollaboratorParticipantId, null,  ddpKitRequestId, "SALIVA", DDPInstance.from(ddpInstanceDto), "100");
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
    public void testVerify_notVerifyMissingOldCollaboratorSampleId() {
        LegacyKitUpdateCollabIdRequest wrongKitUpdateCollabRequest = new LegacyKitUpdateCollabIdRequest(null,
                newCollaboratorSampleId, newCollaboratorParticipantId, shortId, legacyShortId);
        try {
            wrongKitUpdateCollabRequest.verify(ddpInstanceDto, new KitRequestDao());
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertEquals("Missing required field: currentCollaboratorSampleId", e.getMessage());
            return;
        }
        Assert.fail("Should have thrown exception");
    }

    @Test
    public void testVerify_notVerifyMissingNewCollaboratorSampleId() {
        LegacyKitUpdateCollabIdRequest wrongKitUpdateCollabRequest = new LegacyKitUpdateCollabIdRequest(oldCollaboratorSampleId,
                null, newCollaboratorParticipantId, shortId, legacyShortId);
        try {
            wrongKitUpdateCollabRequest.verify(ddpInstanceDto, new KitRequestDao());
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertEquals("Missing required field: newCollaboratorSampleId", e.getMessage());
            return;
        }
        Assert.fail("Should have thrown exception");
    }

    @Test
    public void testVerify_notVerifyMissingShortId() {
        LegacyKitUpdateCollabIdRequest wrongKitUpdateCollabRequest = new LegacyKitUpdateCollabIdRequest(oldCollaboratorSampleId,
                newCollaboratorSampleId, newCollaboratorParticipantId, null, legacyShortId);
        try {
            wrongKitUpdateCollabRequest.verify(ddpInstanceDto, new KitRequestDao());
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertEquals("Missing required field: shortId", e.getMessage());
            return;
        }
        Assert.fail("Should have thrown exception");
    }

    @Test
    public void testVerify_notVerifyMissingNewCollaboratorParticipantId() {
        LegacyKitUpdateCollabIdRequest wrongKitUpdateCollabRequest = new LegacyKitUpdateCollabIdRequest(oldCollaboratorSampleId,
                newCollaboratorSampleId, null, shortId, legacyShortId);
        try {
            wrongKitUpdateCollabRequest.verify(ddpInstanceDto, new KitRequestDao());
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
        LegacyKitUpdateCollabIdRequest wrongParticipantLegacyKitUpdateCollabIdRequest = new LegacyKitUpdateCollabIdRequest(
                oldCollaboratorSampleId, newCollaboratorSampleId, newCollaboratorParticipantId, wrongShortId, legacyShortId);
        try {
            wrongParticipantLegacyKitUpdateCollabIdRequest.verify(ddpInstanceDto, new KitRequestDao());
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertEquals("Invalid participant short ID %s".formatted(wrongShortId), e.getMessage());
            return;
        }
    }

    @Test
    public void testVerify_notVerifyWrongLegacyShortId() {
        String wrongLegacyShortId = "WRONG_LEGACY_SHORT_ID";
        LegacyKitUpdateCollabIdRequest wrongLegacyIdLegacyKitUpdateCollabIdRequest = new LegacyKitUpdateCollabIdRequest(
                oldCollaboratorSampleId, newCollaboratorSampleId, newCollaboratorParticipantId, shortId, wrongLegacyShortId);

        try {
            wrongLegacyIdLegacyKitUpdateCollabIdRequest.verify(ddpInstanceDto, new KitRequestDao());
        } catch (Exception e2) {
            e2.printStackTrace();
            Assert.assertEquals(("Legacy short ID %s does not match legacy short ID on file %s for participant short ID %s, "
                    + " will not update kit %s")
                    .formatted(wrongLegacyShortId, legacyShortId, shortId, oldCollaboratorSampleId), e2.getMessage());
            return;
        }
        Assert.fail("Should have thrown exception");
    }

    @Test
    public void testVerify_notVerifyDuplicateCollaboratorSampleID() {
        String collaboratorSampleId = "DUP_COLLABORATOR_SAMPLE_ID";
        String dsmKitRequestId = testKitUtil.createKitRequestShipping(ddpParticipantId, collaboratorSampleId,
                newCollaboratorParticipantId, null,  "NEW_DUP_DDP_KIT", "SALIVA", DDPInstance.from(ddpInstanceDto), "100");
        createdKits.add(dsmKitRequestId);
        LegacyKitUpdateCollabIdRequest duplicateSampleIdRequest = new LegacyKitUpdateCollabIdRequest(oldCollaboratorSampleId,
                collaboratorSampleId, newCollaboratorParticipantId, shortId, legacyShortId);
        try {
            duplicateSampleIdRequest.verify(ddpInstanceDto, new KitRequestDao());
        } catch (Exception e2) {
            e2.printStackTrace();
            Assert.assertEquals(("Kit request with the new collaboratorSampleId %s already exists!").formatted(collaboratorSampleId),
                    e2.getMessage());
            return;
        }
        Assert.fail("Should have thrown exception");
    }

    @Test
    public void testVerifyAndUpdateCollab() {
        LegacyKitUpdateCollabIdRequest
                legacyKitUpdateCollabIdRequest = new LegacyKitUpdateCollabIdRequest(oldCollaboratorSampleId, newCollaboratorSampleId,
                newCollaboratorParticipantId, shortId, legacyShortId);
        try {
            legacyKitUpdateCollabIdRequest.verify(ddpInstanceDto, new KitRequestDao());
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Should not have thrown exception");
        }
        LegacyKitUpdateCollabIdList legacyKitUpdateCollabIdList = new LegacyKitUpdateCollabIdList(List.of(legacyKitUpdateCollabIdRequest));
        String reqJson = new Gson().toJson(legacyKitUpdateCollabIdList);
        updateKitToLegacyIdService.initialize("test_user", instanceName, null, reqJson);
        UpdateLog updateLog = updateKitToLegacyIdService.changeKitIdsToLegacyIds(legacyKitUpdateCollabIdRequest);
        Assert.assertEquals(UpdateLog.UpdateStatus.ES_UPDATED, updateLog.getStatus());

        KitRequestShipping kitRequestShipping = KitRequestShipping.getKitRequest(Integer.parseInt(createdKits.get(0)));
        Assert.assertEquals(newCollaboratorSampleId, kitRequestShipping.getBspCollaboratorSampleId());
        Assert.assertEquals(newCollaboratorParticipantId, kitRequestShipping.getBspCollaboratorParticipantId());
        Assert.assertEquals(legacyParticipantPair.getRight(), kitRequestShipping.getDdpParticipantId());
        Map<String, Object> participantDsm = ElasticSearchService.getDsmForSingleParticipant(instanceName,
                esIndex, legacyKitUpdateCollabIdRequest.getShortId());
        List<Map<String, Object>> kitRequests = (List<Map<String, Object>>) participantDsm.get("kitRequestShipping");
        kitRequests.stream().filter(kitRequest -> kitRequest.get("bspCollaboratorSampleId").equals(oldCollaboratorSampleId))
                .findFirst().ifPresent(kitRequest -> Assert.fail("Old collaborator sample id should not be present"));
        kitRequests.stream().filter(kitRequest -> kitRequest.get("bspCollaboratorParticipantId")
                        .equals(oldCollaboratorParticipantId)).findAny()
                .ifPresent(kitRequest -> Assert.fail("Old collaborator participant id should not be present"));
        Assert.assertTrue(kitRequests.stream().anyMatch(kitRequest -> kitRequest.get("bspCollaboratorSampleId")
                .equals(newCollaboratorSampleId)));
        Assert.assertTrue(kitRequests.stream().anyMatch(kitRequest -> kitRequest.get("bspCollaboratorParticipantId")
                .equals(newCollaboratorParticipantId)));
        Assert.assertTrue(kitRequests.stream().anyMatch(kitRequest -> kitRequest.getOrDefault("ddpParticipantId", "")
                .equals(legacyParticipantPair.getRight())));
    }

}
