package org.broadinstitute.dsm.model.patch;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.util.NotificationUtil;

@Slf4j
public class DeleteOncHistoryPatch extends ExistingOncHistoryPatch {
    private final NotificationUtil notificationUtil;
    public DeleteOncHistoryPatch(Patch patch, NotificationUtil notificationUtil) {
        super(patch, notificationUtil);
        this.notificationUtil = notificationUtil;
        this.dbElementBuilder = new DefaultDBElementBuilder();
    }

    @Override
    public Object doPatch() {
        Object o = super.doPatch();
        DeletePatchFactory.setDeletedForChildrenFields(this.patch, notificationUtil);
        return o;
    }
}
