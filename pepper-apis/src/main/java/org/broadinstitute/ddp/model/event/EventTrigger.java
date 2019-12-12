package org.broadinstitute.ddp.model.event;

import org.broadinstitute.ddp.db.dto.EventConfigurationDto;


public class EventTrigger {
    private EventConfigurationDto eventConfigurationDto;

    EventTrigger(EventConfigurationDto eventConfigurationDto) {
        this.eventConfigurationDto = eventConfigurationDto;
    }

    public EventConfigurationDto getEventConfigurationDto() {
        return eventConfigurationDto;
    }
}
