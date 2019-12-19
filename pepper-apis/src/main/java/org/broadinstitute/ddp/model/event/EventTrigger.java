package org.broadinstitute.ddp.model.event;

import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.jdbi.v3.core.Handle;


public class EventTrigger {
    private EventConfigurationDto eventConfigurationDto;

    EventTrigger(EventConfigurationDto eventConfigurationDto) {
        this.eventConfigurationDto = eventConfigurationDto;
    }

    public EventConfigurationDto getEventConfigurationDto() {
        return eventConfigurationDto;
    }

    public boolean isTriggered(Handle handle, EventSignal eventSignal) {
        return true;
    }
}
