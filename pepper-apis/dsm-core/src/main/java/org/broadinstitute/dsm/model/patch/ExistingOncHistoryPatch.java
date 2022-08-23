package org.broadinstitute.dsm.model.patch;

import org.broadinstitute.dsm.util.NotificationUtil;

public class ExistingOncHistoryPatch extends ExistingRecordPatch {

    public ExistingOncHistoryPatch(Patch patch, NotificationUtil notificationUtil) {
        super(patch, notificationUtil);
        this.dbElementBuilder = new OncHistoryDBElementBuilder();
    }

}
