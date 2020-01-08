package org.broadinstitute.ddp.model.event;

import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.model.activity.types.DsmNotificationEventType;

public class DsmNotificationTrigger extends EventTrigger {
    private DsmNotificationEventType dsmNotificationEventType;

    public DsmNotificationTrigger(EventConfigurationDto dto) {
        super(dto);
        this.dsmNotificationEventType =
                DsmNotificationEventType.valueOf(dto.getDsmNotificationEventType());
    }

    public DsmNotificationEventType getDsmNotificationEventType() {
        return dsmNotificationEventType;
    }
}
