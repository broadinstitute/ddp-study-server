package org.broadinstitute.ddp.model.event;

import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.jdbi.v3.core.Handle;

public class MarkActivitiesReadOnlyEventAction extends EventAction {

    public MarkActivitiesReadOnlyEventAction(EventConfiguration eventConfiguration, EventConfigurationDto dto) {
        super(eventConfiguration, dto);
    }

    @Override
    public void doAction(PexInterpreter interpreter, Handle handle, EventSignal signal) {
    }
}
