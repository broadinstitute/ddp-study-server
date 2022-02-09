package org.broadinstitute.ddp.model.event;

import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.jdbi.v3.core.Handle;

public class UserStatusChangedTrigger extends EventTrigger {

    private EnrollmentStatusType targetStatusType;

    public UserStatusChangedTrigger(EventConfigurationDto dto) {
        super(dto);
        this.targetStatusType = dto.getUserStatusChangedTargetStatusType();
    }

    public EnrollmentStatusType getTargetStatusType() {
        return targetStatusType;
    }

    @Override
    public boolean isTriggered(Handle handle, EventSignal eventSignal) {
        if (eventSignal.getEventTriggerType() == EventTriggerType.USER_STATUS_CHANGED) {
            var signal = (UserStatusChangedSignal) eventSignal;
            return signal.getNewStatusType() == targetStatusType;
        } else {
            return false;
        }
    }
}
