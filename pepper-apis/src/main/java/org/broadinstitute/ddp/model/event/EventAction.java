package org.broadinstitute.ddp.model.event;

import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class EventAction {
    private static final Logger LOG = LoggerFactory.getLogger(EventAction.class);

    MessageDestination gcpTopic;
    EventConfiguration eventConfiguration;


    public EventAction(EventConfiguration eventConfiguration, EventConfigurationDto dto) {
        this.eventConfiguration = eventConfiguration;
        this.gcpTopic = dto.getGcpTopic() == null ? null : MessageDestination.valueOf(dto.getGcpTopic());
    }

    public MessageDestination getGcpTopic() {
        return gcpTopic;
    }

    public abstract void doAction(PexInterpreter pexInterpreter, Handle handle, EventSignal eventSignal);
}

