package org.broadinstitute.ddp.model.event;

import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.model.activity.types.DsmNotificationEventType;
import org.jdbi.v3.core.Handle;

public class DsmNotificationTrigger extends EventTrigger {

    private DsmNotificationEventType eventType;

    public DsmNotificationTrigger(EventConfigurationDto dto) {
        super(dto);
        this.eventType = dto.getDsmNotificationEventType();
    }

    public DsmNotificationEventType getDsmEventType() {
        return eventType;
    }

    @Override
    public boolean isTriggered(Handle handle, EventSignal eventSignal) {
        if (eventSignal instanceof DsmNotificationSignal) {
            DsmNotificationSignal dsmSignal = (DsmNotificationSignal) eventSignal;
            if (dsmSignal.getDsmEventType() == DsmNotificationEventType.TEST_RESULT) {
                return eventType == dsmSignal.getDsmEventType() && dsmSignal.getTestResult() != null;
            } else {
                return eventType == dsmSignal.getDsmEventType();
            }
        } else {
            return false;
        }
    }
}
