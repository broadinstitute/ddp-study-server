package org.broadinstitute.ddp.model.event;

import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.jdbi.v3.core.Handle;

public abstract class EventAction {

    protected MessageDestination gcpTopic;
    protected EventConfiguration eventConfiguration;

    public EventAction(EventConfiguration eventConfiguration, EventConfigurationDto dto) {
        this.eventConfiguration = eventConfiguration;
        this.gcpTopic = (dto == null || dto.getGcpTopic() == null) ? null : MessageDestination.valueOf(dto.getGcpTopic());
    }

    public MessageDestination getGcpTopic() {
        return gcpTopic;
    }

    public abstract void doAction(PexInterpreter interpreter, Handle handle, EventSignal signal);
}
