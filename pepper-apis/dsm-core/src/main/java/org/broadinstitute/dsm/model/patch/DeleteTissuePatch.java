package org.broadinstitute.dsm.model.patch;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.util.NotificationUtil;

@Slf4j
public class DeleteTissuePatch extends ExistingRecordPatch {
    public DeleteTissuePatch(Patch patch, NotificationUtil notificationUtil) {
        super(patch, notificationUtil);
    }

    @Override
    public Object doPatch() {
        Object o = null;
        try {
            o = super.doPatch();
        } finally {
            DeletePatchFactory.deleteChildrenFields(this.patch, this.getNotificationUtil());
            return o;
        }

    }

}
