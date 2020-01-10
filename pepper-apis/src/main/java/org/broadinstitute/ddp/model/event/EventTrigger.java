package org.broadinstitute.ddp.model.event;

import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.jdbi.v3.core.Handle;


public class EventTrigger<T extends EventSignal> {
    private EventConfigurationDto eventConfigurationDto;

    EventTrigger(EventConfigurationDto eventConfigurationDto) {
        this.eventConfigurationDto = eventConfigurationDto;
    }

    public EventConfigurationDto getEventConfigurationDto() {
        return eventConfigurationDto;
    }

    public boolean isTriggered(Handle handle, T eventSignal) {
        return true;
    }

    @Override
    public String toString() {
        return "EventTrigger{type=" + eventConfigurationDto.getEventTriggerType() + '}';
    }
}
