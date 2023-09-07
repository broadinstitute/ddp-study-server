package org.broadinstitute.dsm.model.patch;

import static org.broadinstitute.dsm.statics.DBConstants.DDP_ONC_HISTORY_ALIAS;
import static org.broadinstitute.dsm.statics.DBConstants.DDP_TISSUE_ALIAS;

import java.util.ArrayList;
import java.util.List;

import lombok.NonNull;
import org.apache.commons.lang3.NotImplementedException;
import org.broadinstitute.dsm.db.dao.ddp.tissue.TissueDao;
import org.broadinstitute.dsm.db.dao.ddp.tissue.TissueSMIDDao;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.util.NotificationUtil;

public class DeletePatchFactory {

    private DeletePatchFactory() {
        throw new IllegalStateException("Utility class");
    }

    public static BasePatch produce(Patch patch, NotificationUtil notificationUtil) {
        BasePatch patcher;
        if (DDP_ONC_HISTORY_ALIAS.equals(patch.getTableAlias())) {
            patcher = new DeleteOncHistoryPatch(patch, notificationUtil);
        } else if (PatchFactory.isTissueRelatedOncHistoryId(patch)) {
            patcher = new DeleteTissuePatch(patch, notificationUtil);
        } else {
            throw new NotImplementedException("This method should not reach here");
        }
        return patcher;
    }

    protected static void setDeletedForChildrenFields(@NonNull Patch originalPatch, NotificationUtil notificationUtil) {
        List<Patch> deletePatches = null;
        if (originalPatch.getNameValue().getName().equals("t.deleted") && originalPatch.getTableAlias().equals(DDP_TISSUE_ALIAS)) {
            deletePatches = getPatchForSmIds(originalPatch);
        } else if (originalPatch.getNameValue().getName().equals("oD.deleted") &&
                originalPatch.getTableAlias().equals(DDP_ONC_HISTORY_ALIAS)) {
            deletePatches = getPatchForTissues(originalPatch);
        }
        for (Patch childPatch : deletePatches) {
            BasePatch patcher = PatchFactory.makePatch(childPatch, notificationUtil);
            patcher.doPatch();
        }
    }

    private static List<Patch> getPatchForTissues(Patch oncHistoryPatch) {
        List<String> tissueIds = TissueDao.getTissuesByOncHistoryDetailId(oncHistoryPatch.getId());
        List<Patch> deletePatches = new ArrayList<>();
        for (String tissueId : tissueIds) {
            NameValue nameValue = new NameValue("t.deleted", "1");
            deletePatches.add(
                    new Patch(tissueId, "oncHistoryDetailId", oncHistoryPatch.getId(), oncHistoryPatch.getUser(), nameValue, null, true,
                            oncHistoryPatch.getDdpParticipantId(), oncHistoryPatch.getRealm()));
        }
        return deletePatches;
    }

    private static List<Patch> getPatchForSmIds(Patch tissuePatch) {
        List<String> smIds = TissueSMIDDao.getSmIdPksForTissue(tissuePatch.getId());
        List<Patch> deletePatches = new ArrayList<>();
        for (String smIdPk : smIds) {
            NameValue nameValue = new NameValue("sm.deleted", "1");
            deletePatches.add(
                    new Patch(smIdPk, "tissueId", tissuePatch.getId(), tissuePatch.getUser(), nameValue, null, true,
                            tissuePatch.getDdpParticipantId(), tissuePatch.getRealm()));
        }
        return deletePatches;
    }
}
