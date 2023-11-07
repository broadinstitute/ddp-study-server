package org.broadinstitute.dsm.model.patch;


import java.util.ArrayList;
import java.util.List;

import lombok.NonNull;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.util.NotificationUtil;

public class DeletePatchFactory {

    public static BasePatch produce(Patch patch, NotificationUtil notificationUtil) {
        BasePatch patcher;
        if (PatchFactory.isOncHistoryDetailPatch(patch)) {
            patcher = new DeletePatch(patch, notificationUtil, DeleteType.ONC_HISTORY_DETAIL);
        } else if (PatchFactory.isTissueRelatedOncHistoryId(patch)) {
            patcher = new DeletePatch(patch, notificationUtil, DeleteType.TISSUE);
        }  else if (Patch.isSmIdDeletePatch(patch)) {
            patcher = new DeletePatch(patch, notificationUtil, DeleteType.SM_ID);
        } else {
            throw new DsmInternalError("No support for deleting patch for nameValue: "+ patch.getNameValue());
        }
        return patcher;
    }

    public static void deleteChildrenFields(@NonNull Patch originalPatch, NotificationUtil notificationUtil) {
        List<Patch> deletePatches = new ArrayList<>();
        if (Patch.isTissueDeletePatch(originalPatch)) {
            deletePatches = Patch.getPatchForSmIds(originalPatch);
        } else if (Patch.isOncHistoryDeletePatch(originalPatch)) {
            deletePatches = Patch.getPatchForTissues(originalPatch);
        }
        for (Patch childPatch : deletePatches) {
            BasePatch patcher = PatchFactory.makePatch(childPatch, notificationUtil);
            patcher.doPatch();
        }
    }
}
