package org.broadinstitute.ddp.model.event;

import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;

public class UserStatusChangedSignal extends EventSignal {

    private EnrollmentStatusType newStatusType;

    public UserStatusChangedSignal(long operatorId, long participantId, String participantGuid,
                                   String operatorGuid, long studyId, String studyGuid, EnrollmentStatusType newStatusType) {
        super(operatorId, participantId, participantGuid, operatorGuid, studyId, studyGuid, EventTriggerType.USER_STATUS_CHANGED);
        this.newStatusType = newStatusType;
    }

    public EnrollmentStatusType getNewStatusType() {
        return newStatusType;
    }
}
