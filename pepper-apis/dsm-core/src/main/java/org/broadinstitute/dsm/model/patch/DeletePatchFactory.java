package org.broadinstitute.dsm.model.patch;

import java.util.ArrayList;
import java.util.List;

import lombok.NonNull;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.dao.ddp.tissue.TissueDao;
import org.broadinstitute.dsm.db.dao.ddp.tissue.TissueSMIDDao;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.NotificationUtil;

public class DeletePatchFactory {
    private static final String ONC_HISTORY_DELETED_FIELD = "oD.deleted";
    private static final String TISSUE_DELETED_FIELD = "t.deleted";
    private static final String SM_ID_DELETED_FIELD = "sm.deleted";
    private static final String TRUE_FLAG = "1";
    private DeletePatchFactory() {
        throw new IllegalStateException("Utility class");
    }

    public static BasePatch produce(Patch patch, NotificationUtil notificationUtil) {
        BasePatch patcher;
        if (DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS.equals(patch.getTableAlias())) {
            patcher = new DeleteOncHistoryPatch(patch, notificationUtil);
        } else if (PatchFactory.isTissueRelatedOncHistoryId(patch)) {
            patcher = new DeleteTissuePatch(patch, notificationUtil);
        } else {
            throw new DsmInternalError("This method should not reach here");
        }
        return patcher;
    }

    protected static void deleteChildrenFields(@NonNull Patch originalPatch, NotificationUtil notificationUtil) {
        List<Patch> deletePatches = new ArrayList<>();
        if (isTissuePatch(originalPatch)) {
            deletePatches = getPatchForSmIds(originalPatch);
        } else if (isOncHistoryPatch(originalPatch)) {
            deletePatches = getPatchForTissues(originalPatch);
        }
        for (Patch childPatch : deletePatches) {
            BasePatch patcher = PatchFactory.makePatch(childPatch, notificationUtil);
            patcher.doPatch();
        }
    }

    private static boolean isOncHistoryPatch(Patch originalPatch) {
        return originalPatch.getNameValue().getName().equals(ONC_HISTORY_DELETED_FIELD) &&
                originalPatch.getTableAlias().equals(DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS);
    }

    private static boolean isTissuePatch(Patch originalPatch) {
        return originalPatch.getNameValue().getName().equals(TISSUE_DELETED_FIELD) &&
                originalPatch.getTableAlias().equals(DBConstants.DDP_TISSUE_ALIAS);
    }

    private static List<Patch> getPatchForTissues(Patch oncHistoryPatch) {
        List<String> tissueIds = TissueDao.getTissuesByOncHistoryDetailId(oncHistoryPatch.getId());
        List<Patch> deletePatches = new ArrayList<>();
        for (String tissueId : tissueIds) {
            NameValue nameValue = new NameValue(TISSUE_DELETED_FIELD, TRUE_FLAG);
            Patch patch =
                    new Patch(tissueId, OncHistoryDetail.ONC_HISTORY_DETAIL_ID, oncHistoryPatch.getId(), oncHistoryPatch.getUser(), nameValue, null, true,
                            oncHistoryPatch.getDdpParticipantId(), oncHistoryPatch.getRealm());
            patch.setTableAlias(DBConstants.DDP_TISSUE_ALIAS);
            deletePatches.add(patch);
        }
        return deletePatches;
    }

    private static List<Patch> getPatchForSmIds(Patch tissuePatch) {
        List<String> smIds = TissueSMIDDao.getSmIdPksForTissue(tissuePatch.getId());
        List<Patch> deletePatches = new ArrayList<>();
        for (String smIdPk : smIds) {
            NameValue nameValue = new NameValue(SM_ID_DELETED_FIELD, TRUE_FLAG);
            deletePatches.add(
                    new Patch(smIdPk, TissuePatch.TISSUE_ID, tissuePatch.getId(), tissuePatch.getUser(), nameValue, null, true,
                            tissuePatch.getDdpParticipantId(), tissuePatch.getRealm()));
        }
        return deletePatches;
    }
}
