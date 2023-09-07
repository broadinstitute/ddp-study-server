package org.broadinstitute.dsm.model.patch;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.util.NotificationUtil;

@Slf4j
public class DeleteOncHistoryPatch extends OncHistoryDetailPatch {
    private final NotificationUtil notificationUtil;
    public DeleteOncHistoryPatch(Patch patch, NotificationUtil notificationUtil) {
        super(patch);
        this.notificationUtil = notificationUtil;
        this.dbElementBuilder = new OncHistoryDBElementBuilder();
    }

    @Override
    public Object doPatch() {
        Object o = super.doPatch();
        DeletePatchFactory.setDeletedForChildrenFields(this.patch, notificationUtil);
        return o;
    }
}
