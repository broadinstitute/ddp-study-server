package org.broadinstitute.dsm.patch;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.DbTxnBaseTest;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.SmId;
import org.broadinstitute.dsm.db.Tissue;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.patch.BasePatch;
import org.broadinstitute.dsm.model.patch.DeleteOncHistoryPatch;
import org.broadinstitute.dsm.model.patch.DeletePatchFactory;
import org.broadinstitute.dsm.model.patch.DeleteTissuePatch;
import org.broadinstitute.dsm.model.patch.Patch;
import org.broadinstitute.dsm.model.patch.PatchFactory;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

@Slf4j
public class DeletePatchTest extends DbTxnBaseTest {
    private static final String participantGuid = "TEST_PARTICIPANT";
    private static String participantId;
    private static String instanceName = "testInstance";
    private static List<String> createdOncHistoryIds = new ArrayList<>();
    private static List<String> createdTissueIds = new ArrayList<>();
    private static List<String> createdSmIdPks = new ArrayList<>();
    private static TestPatchUtil testPatchUtil = new TestPatchUtil(instanceName, "testInstanceGuid", "", "test");

    @BeforeClass
    public static void createParticipantAndMedicalRecords() {
        participantId = testPatchUtil.createParticipantForStudy("TEST_PARTICIPANT");
    }

    @AfterClass
    public static void removeEverything() {
        testPatchUtil.deleteSmId(createdSmIdPks);
        testPatchUtil.deleteTissue(createdTissueIds);
        List<String> participantIds = new ArrayList<>();
        participantIds.add(participantGuid);
        testPatchUtil.deleteOncHistoryDetailAndMedicalRecord(createdOncHistoryIds, participantIds);
    }

    @Test
    public void testIsDeletePatchMethod() {
        String oncHistoryDeleteJson =
                "{\"id\":\"123\",\"user\":\"user1\",\"nameValue\":{\"name\":\"oD.deleted\",\"value\":true},\"ddpParticipantId\":"
                        + "\"TEST_PARTICIPANT\",\"parent\":\"participantId\",\"parentId\":16548,\"tableAlias\":\"oD\","
                        + "\"isUnique\":true,\"realm\":\"SOME_REALM\"}";
        Patch oncHistoryDeletePatch = new Gson().fromJson(oncHistoryDeleteJson, Patch.class);
        Assert.assertTrue(PatchFactory.isDeletePatch(oncHistoryDeletePatch));

        String notOncHistoryDeleteJson =
                "{\"id\":\"123\",\"user\":\"user1\",\"nameValue\":{\"name\":\"oD.somethingElse\",\"value\":true},\"ddpParticipantId\":"
                        + "\"TEST_PARTICIPANT\",\"parent\":\"participantId\",\"parentId\":16548,\"tableAlias\":\"oD\",\"isUnique\":true,"
                        + "\"realm\":\"SOME_REALM\"}";
        Patch notOncHistoryDeletePatch = new Gson().fromJson(notOncHistoryDeleteJson, Patch.class);
        Assert.assertFalse(PatchFactory.isDeletePatch(notOncHistoryDeletePatch));

        String tissueDeleteJson =
                "{\"id\":\"123\",\"user\":\"user1\",\"nameValue\":{\"name\":\"t.deleted\",\"value\":\"1\"},\"ddpParticipantId\":"
                        + "\"TEST_PARTICIPANT\",\"parent\":\"oncHistoryDetailId\",\"parentId\":2071,\"tableAlias\":\"t\","
                        + "\"isUnique\":true,\"realm\":\"osteo2\"}";
        Patch tissueDeletePatch = new Gson().fromJson(tissueDeleteJson, Patch.class);
        Assert.assertTrue(PatchFactory.isDeletePatch(tissueDeletePatch));

        String notDeleteJson =
                "{\"id\":\"123\",\"user\":\"user1\",\"nameValue\":{\"name\":\"t.somethingElse\",\"value\":\"1\"},\"ddpParticipantId\""
                        +
                        ":\"TEST_PARTICIPANT\",\"parent\":\"oncHistoryDetailId\",\"parentId\":2071,\"tableAlias\":\"t\",\"isUnique\":true,"
                        + "\"realm\":\"osteo2\"}";
        Patch notTissueDeletePatch = new Gson().fromJson(notDeleteJson, Patch.class);
        Assert.assertFalse(PatchFactory.isDeletePatch(notTissueDeletePatch));
    }

    @Test
    public void testPatchFactoryProduce() {
        String oncHistoryDeleteJson = "{\"id\":\"123\",\"user\":\"user1\",\"nameValue\":{\"name\":\"oD.deleted\",\"value\":true},"
                + "\"ddpParticipantId\":\"TEST_PARTICIPANT\",\"parent\":\"participantId\",\"parentId\":16548,\"tableAlias\":\"oD\""
                + ",\"isUnique\":true,\"realm\":\"SOME_REALM\"}";
        Patch oncHistoryDeletePatch = new Gson().fromJson(oncHistoryDeleteJson, Patch.class);
        BasePatch patcher = PatchFactory.makePatch(oncHistoryDeletePatch, null);
        Assert.assertTrue(patcher instanceof DeleteOncHistoryPatch);

        String tissueDeleteJson =
                "{\"id\":\"123\",\"user\":\"user1\",\"nameValue\":{\"name\":\"t.deleted\",\"value\":\"1\"},\"ddpParticipantId\":\"TEST_PARTICIPANT\",\"parent\":\"oncHistoryDetailId\",\"parentId\":2071,\"tableAlias\":\"t\",\"isUnique\":true,\"realm\":\"osteo2\"}";
        Patch tissueDeletePatch = new Gson().fromJson(tissueDeleteJson, Patch.class);
        patcher = PatchFactory.makePatch(tissueDeletePatch, null);
        Assert.assertTrue(patcher instanceof DeleteTissuePatch);
    }

    @Test
    public void testDeleteOncHistoryAndUniquePatch() {
        String institutionId = testPatchUtil.createInstitutionForParticipant(participantId);
        String medicalRecordId = testPatchUtil.createMRForParticipant(institutionId);
        String oncHistoryId = testPatchUtil.createOncHistoryForParticipant(medicalRecordId);
        createdOncHistoryIds.add(oncHistoryId);
        String tissueId = testPatchUtil.createTissueForParticipant(oncHistoryId);
        createdTissueIds.add(tissueId);
        String smIdPk = testPatchUtil.createSmIdForParticipant(tissueId, "USS_VALUE");
        createdSmIdPks.add(smIdPk);
        List<String> participantIds = new ArrayList<>();
        participantIds.add(participantGuid);
        OncHistoryDetail oncHistoryDetail = testPatchUtil.getOncHistoryDetailById(oncHistoryId);
        Assert.assertFalse(oncHistoryDetail.isDeleted());
        Tissue tissue = oncHistoryDetail.getTissues().stream().findFirst().get();
        Assert.assertEquals(String.valueOf(tissue.getTissueId()), tissueId);
        SmId tissueSmId = tissue.getUssSMID().stream().filter(smId -> String.valueOf(smId.getSmIdPk()).equals(smIdPk)).findAny().get();
        Assert.assertEquals(String.valueOf(tissueSmId.getTissueId()), tissueId);

        String tissueSkIdUniquePatch = "{\"id\":" + tissueId + ",\"user\":\"" + testPatchUtil.userEmail +
                "\",\"nameValue\":{\"name\":\"t.skId\",\"value\":\"123\"},\"ddpParticipantId\":\""
                + participantGuid + "\",\"parent\":\"oncHistoryDetailId\",\"parentId\":" + oncHistoryId +
                ",\"tableAlias\":\"t\",\"isUnique\":true,\"realm\":\"" + instanceName + "\"}";
        Patch patch = new Gson().fromJson(tissueSkIdUniquePatch, Patch.class);
        BasePatch patcher = PatchFactory.makePatch(patch, null);
        try {
            patcher.doPatch();
        } catch (Exception e){
            log.error("", e);
        }

        tissueSkIdUniquePatch = "{\"id\":" + tissueId + ",\"user\":\"" + testPatchUtil.userEmail +
                "\",\"nameValue\":{\"name\":\"t.skId\",\"value\":\"123\"},\"ddpParticipantId\":\""
                + participantGuid + "\",\"parent\":\"oncHistoryDetailId\",\"parentId\":" + oncHistoryId +
                ",\"tableAlias\":\"t\",\"isUnique\":true,\"realm\":\"" + instanceName + "\"}";
        patch = new Gson().fromJson(tissueSkIdUniquePatch, Patch.class);
        patcher = PatchFactory.makePatch(patch, null);
        try {
            patcher.doPatch();
        } catch (Exception e) {
            Assert.assertTrue(e instanceof DsmInternalError);
        }

        String oncHistoryDeleteJson = "{\"id\":\"" + oncHistoryId + "\",\"user\":\"" + testPatchUtil.userEmail
                + "\",\"nameValue\":{\"name\":\"oD.deleted\",\"value\":true},\"ddpParticipantId\":" + participantGuid
                + ",\"parent\":\"participantId\",\"parentId\":" + participantId
                + ",\"tableAlias\":\"oD\",\"isUnique\":true,\"realm\":\"" + instanceName + "\"}";
        Patch oncHistoryDeletePatch = new Gson().fromJson(oncHistoryDeleteJson, Patch.class);
        patcher = DeletePatchFactory.produce(oncHistoryDeletePatch, null);
        patcher.doPatch();
        oncHistoryDetail = testPatchUtil.getOncHistoryDetailById(oncHistoryId);
        Assert.assertTrue(oncHistoryDetail.isDeleted());
        tissue = oncHistoryDetail.getTissues().stream().findFirst().get();
        Assert.assertTrue(tissue.isDeleted());
        tissueSmId = tissue.getUssSMID().stream().filter(smId -> String.valueOf(smId.getSmIdPk()).equals(smIdPk)).findAny().get();
        Assert.assertTrue(tissueSmId.getDeleted());
    }

}
