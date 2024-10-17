package org.broadinstitute.ddp.studybuilder.task;

import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Handle;

@Slf4j
public class LmsInsertGovUserConsentEvents extends InsertStudyEvents {

    public LmsInsertGovUserConsentEvents() {
        super("cmi-lms", "patches/lms-govuser-consent-events.conf");
        log.info("TASK:: LmsInsertGovUserConsentEvents ");
    }

    @Override
    public void run(final Handle handle) {
        super.run(handle);
    }

}
