package org.broadinstitute.dsm.model.patch;

import static org.broadinstitute.dsm.statics.DBConstants.DDP_ONC_HISTORY_ALIAS;
import static org.broadinstitute.dsm.statics.DBConstants.DDP_PARTICIPANT_RECORD_ALIAS;

import org.broadinstitute.dsm.util.NotificationUtil;

public class ExistingRecordPatchFactory {

    public static BasePatch produce(Patch patch, NotificationUtil notificationUtil) {
        BasePatch patcher;
        if (DDP_PARTICIPANT_RECORD_ALIAS.equals(patch.getTableAlias())) {
            patcher = new ExistingParticipantRecordPatch(patch, notificationUtil);
        } else if (DDP_ONC_HISTORY_ALIAS.equals(patch.getTableAlias())) {
            patcher = new ExistingOncHistoryPatch(patch, notificationUtil);
        } else {
            patcher = new ExistingRecordPatch(patch, notificationUtil);
        }
        return patcher;
    }
}
