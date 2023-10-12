package org.broadinstitute.dsm.patch;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import org.broadinstitute.dsm.DbTxnBaseTest;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.SmId;
import org.broadinstitute.dsm.db.Tissue;
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

public class DeletePatchTest extends DbTxnBaseTest {
    private static final String participantGuid = "TEST_PARTICIPANT";
    private static String participantId;
    private static String instanceName = "testInstance";
    private static List<String> createdOncHistoryIds = new ArrayList<>();
    private static List<String> createdTissueIds = new ArrayList<>();
    private static List<String> createdSmIdPks = new ArrayList<>();
    private static TestParticipantUtil testParticipantUtil = new TestParticipantUtil(instanceName, "testInstanceGuid", "", "test");

    @BeforeClass
    public static void createParticipantAndMedicalRecords() {
        participantId = testParticipantUtil.createParticipantForStudy("TEST_PARTICIPANT");
    }

    @AfterClass
    public static void removeEverything() {
        testParticipantUtil.deleteSmId(createdSmIdPks);
        testParticipantUtil.deleteTissue(createdTissueIds);
        List<String> participantIds = new ArrayList<>();
        participantIds.add(participantGuid);
        testParticipantUtil.deleteOncHistoryDetailAndMedicalRecord(createdOncHistoryIds, participantIds);
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
    public void testDeleteOncHistoryPatch() {
        String institutionId = testParticipantUtil.createInstitutionForParticipant(participantId);
        String medicalRecordId = testParticipantUtil.createMRForParticipant(institutionId);
        String oncHistoryId = testParticipantUtil.createOncHistoryForParticipant(medicalRecordId);
        createdOncHistoryIds.add(oncHistoryId);
        String tissueId = testParticipantUtil.createTissueForParticipant(oncHistoryId);
        createdTissueIds.add(tissueId);
        String smIdPk = testParticipantUtil.createSmIdForParticipant(tissueId, "USS_VALUE");
        createdSmIdPks.add(smIdPk);
        List<String> participantIds = new ArrayList<>();
        participantIds.add(participantGuid);
        OncHistoryDetail oncHistoryDetail = testParticipantUtil.getOncHistoryDetailById(oncHistoryId);
        Assert.assertFalse(oncHistoryDetail.isDeleted());
        Tissue tissue = oncHistoryDetail.getTissues().stream().findFirst().get();
        Assert.assertEquals(String.valueOf(tissue.getTissueId()), tissueId);
        SmId tissueSmId = tissue.getUssSMID().stream().filter(smId -> String.valueOf(smId.getSmIdPk()).equals(smIdPk)).findAny().get();
        Assert.assertEquals(String.valueOf(tissueSmId.getTissueId()), tissueId);

        String oncHistoryDeleteJson = "{\"id\":\"" + oncHistoryId + "\",\"user\":\"" + testParticipantUtil.userEmail
                + "\",\"nameValue\":{\"name\":\"oD.deleted\",\"value\":true},\"ddpParticipantId\":" + participantGuid
                + ",\"parent\":\"participantId\",\"parentId\":" + participantId
                + ",\"tableAlias\":\"oD\",\"isUnique\":true,\"realm\":\"" + instanceName + "\"}";
        Patch oncHistoryDeletePatch = new Gson().fromJson(oncHistoryDeleteJson, Patch.class);
        BasePatch patcher = DeletePatchFactory.produce(oncHistoryDeletePatch, null);
        patcher.doPatch();
        oncHistoryDetail = testParticipantUtil.getOncHistoryDetailById(oncHistoryId);
        Assert.assertTrue(oncHistoryDetail.isDeleted());
        tissue = oncHistoryDetail.getTissues().stream().findFirst().get();
        Assert.assertTrue(tissue.getDeleted());
        tissueSmId = tissue.getUssSMID().stream().filter(smId -> String.valueOf(smId.getSmIdPk()).equals(smIdPk)).findAny().get();
        Assert.assertTrue(tissueSmId.getDeleted());
    }

}
