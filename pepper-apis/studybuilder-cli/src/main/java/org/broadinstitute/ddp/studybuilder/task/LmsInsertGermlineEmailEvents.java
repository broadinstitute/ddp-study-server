package org.broadinstitute.ddp.studybuilder.task;

import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Handle;

@Slf4j
public class LmsInsertGermlineEmailEvents extends InsertStudyEvents {

    public LmsInsertGermlineEmailEvents() {
        super("cmi-lms", "patches/germline-consent-addendum-email-events.conf");
        log.info("TASK:: LmsInsertGermlineEmailEvents ");
    }

    @Override
    public void run(final Handle handle) {
        super.run(handle);
    }

}
