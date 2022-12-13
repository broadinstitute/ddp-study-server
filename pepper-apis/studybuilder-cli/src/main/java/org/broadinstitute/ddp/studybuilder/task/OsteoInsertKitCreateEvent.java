package org.broadinstitute.ddp.studybuilder.task;

import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Handle;

@Slf4j
public class OsteoInsertKitCreateEvent extends InsertStudyEvents {
    public OsteoInsertKitCreateEvent() {
        super("CMI-OSTEO", "patches/osteo-blood-kit-event.conf");
    }

    @Override
    public void run(final Handle handle) {
        super.run(handle);
    }

}
