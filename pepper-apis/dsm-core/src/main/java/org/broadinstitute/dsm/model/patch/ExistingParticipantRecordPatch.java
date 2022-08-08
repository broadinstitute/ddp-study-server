package org.broadinstitute.dsm.model.patch;

import org.broadinstitute.dsm.util.NotificationUtil;

public class ExistingParticipantRecordPatch extends ExistingRecordPatch {

    public ExistingParticipantRecordPatch(Patch patch, NotificationUtil notificationUtil) {
        super(patch, notificationUtil);
        this.dbElementBuilder = new ParticipantRecordDBElementBuilder();
    }
}
