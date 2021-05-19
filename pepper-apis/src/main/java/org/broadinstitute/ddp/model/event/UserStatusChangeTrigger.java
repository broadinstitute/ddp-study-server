package org.broadinstitute.ddp.model.event;

import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.jdbi.v3.core.Handle;

public class UserStatusChangeTrigger extends EventTrigger {

    private EnrollmentStatusType targetStatusType;

    public UserStatusChangeTrigger(EventConfigurationDto dto) {
        super(dto);
        this.targetStatusType = dto.getUserStatusChangeTargetStatusType();
    }

    public EnrollmentStatusType getTargetStatusType() {
        return getTargetStatusType();
    }

    @Override
    public boolean isTriggered(Handle handle, EventSignal eventSignal) {
        if (eventSignal.getEventTriggerType() == EventTriggerType.USER_STATUS_CHANGE) {
            var signal = (UserStatusChangeSignal) eventSignal;
            return signal.getNewStatusType() == targetStatusType;
        } else {
            return false;
        }
    }
}
