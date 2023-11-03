package org.broadinstitute.dsm.patch;

import com.google.gson.Gson;
import org.broadinstitute.dsm.model.patch.Patch;
import org.broadinstitute.dsm.model.patch.PatchFactory;
import org.broadinstitute.dsm.util.TestUtil;
import org.junit.Assert;
import org.junit.Test;

public class DeletePatchTest {
    @Test
    public void testDetectingDeleteRequest() throws Exception {
        String oncHistoryDeleteJson = TestUtil.readFile("patchRequests/oncHistoryDeletePatchRequest.json");
        Patch oncHistoryDeletePatch = new Gson().fromJson(oncHistoryDeleteJson, Patch.class);
        Assert.assertTrue(PatchFactory.isDeletePatch(oncHistoryDeletePatch));


        String tissueDeletePatchJson = TestUtil.readFile("patchRequests/tissueDeletePatchRequest.json");
        Patch tissueDeletePatch = new Gson().fromJson(tissueDeletePatchJson, Patch.class);
        Assert.assertTrue(PatchFactory.isDeletePatch(tissueDeletePatch));

        String smIdDeletePatchJson = TestUtil.readFile("patchRequests/smIdDeletePatchRequest.json");
        Patch smIdPatch = new Gson().fromJson(smIdDeletePatchJson, Patch.class);
        Assert.assertTrue(PatchFactory.isDeletePatch(smIdPatch));
    }

    @Test
    public void testDetectingNotDeleteRequest() throws Exception {
        String oncHistoryJson = TestUtil.readFile("patchRequests/oncHistoryDetailNotDeletePatchRequest.json");
        Patch oncHistoryNotDeletePatch = new Gson().fromJson(oncHistoryJson, Patch.class);
        Assert.assertFalse(PatchFactory.isDeletePatch(oncHistoryNotDeletePatch));

        String tissuePatchJson = TestUtil.readFile("patchRequests/tissueNotDeletePatchRequest.json");
        Patch tissuePatch = new Gson().fromJson(tissuePatchJson, Patch.class);
        Assert.assertFalse(PatchFactory.isDeletePatch(tissuePatch));

        String smIdPatchJson = TestUtil.readFile("patchRequests/smIdNotDeletePatchRequest.json");
        Patch smIdPatchJsonPatch = new Gson().fromJson(smIdPatchJson, Patch.class);
        Assert.assertFalse(PatchFactory.isDeletePatch(smIdPatchJsonPatch));
    }

}