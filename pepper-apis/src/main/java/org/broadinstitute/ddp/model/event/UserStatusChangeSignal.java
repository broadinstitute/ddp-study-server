package org.broadinstitute.ddp.model.event;

import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;

public class UserStatusChangeSignal extends EventSignal {

    private EnrollmentStatusType newStatusType;

    public UserStatusChangeSignal(long operatorId, long participantId, String participantGuid,
                                  String operatorGuid, long studyId, EnrollmentStatusType newStatusType) {
        super(operatorId, participantId, participantGuid, operatorGuid, studyId, EventTriggerType.USER_STATUS_CHANGE);
        this.newStatusType = newStatusType;
    }

    public EnrollmentStatusType getNewStatusType() {
        return newStatusType;
    }
}
