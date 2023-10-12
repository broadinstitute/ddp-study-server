package org.broadinstitute.dsm.model.patch;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.util.NotificationUtil;

@Slf4j
public class DeleteTissuePatch extends ExistingRecordPatch {
    private final NotificationUtil notificationUtil;
    public DeleteTissuePatch(Patch patch, NotificationUtil notificationUtil) {
        super(patch, notificationUtil);
        this.notificationUtil = notificationUtil;
    }

    @Override
    public Object doPatch() {
        Object o = null;
        try {
            o = super.doPatch();
        } finally {
            DeletePatchFactory.setDeletedForChildrenFields(this.patch, notificationUtil);
            return o;
        }

    }

}
