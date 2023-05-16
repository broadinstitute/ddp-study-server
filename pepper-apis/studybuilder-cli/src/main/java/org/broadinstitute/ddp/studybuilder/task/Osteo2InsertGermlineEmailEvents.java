package org.broadinstitute.ddp.studybuilder.task;

import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Handle;

@Slf4j
public class Osteo2InsertGermlineEmailEvents extends InsertStudyEvents {

    private static final String STUDY_GUID = "CMI-OSTEO";

    public Osteo2InsertGermlineEmailEvents() {
        super(STUDY_GUID, "patches/germline-consent-addendum-email-events.conf");
    }

    @Override
    public void run(final Handle handle) {
        super.run(handle);
    }

}
