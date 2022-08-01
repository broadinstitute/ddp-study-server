
package org.broadinstitute.dsm.model.patch;

import static org.broadinstitute.dsm.statics.DBConstants.DDP_PARTICIPANT_RECORD_ALIAS;

import org.broadinstitute.dsm.util.NotificationUtil;

public class ExistingRecordPatchFactory {

    public static BasePatch produce(Patch patch, NotificationUtil notificationUtil) {
        return DDP_PARTICIPANT_RECORD_ALIAS.equals(patch.getTableAlias())
                ? new ExistingParticipantRecordPatch(patch, notificationUtil)
                : new ExistingRecordPatch(patch, notificationUtil);
    }
}
