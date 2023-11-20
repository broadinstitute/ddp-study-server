package org.broadinstitute.ddp.studybuilder.task;

import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Handle;

@Slf4j
public class OsteoInsertUserEnrollmentEvents extends InsertStudyEvents {

    private static final String STUDY_GUID = "CMI-OSTEO";

    public OsteoInsertUserEnrollmentEvents() {
        super(STUDY_GUID, "patches/user-enrollment-events-upd.conf");
        log.info("TASK:: OsteoInsertUserEnrollmentEvents ");
    }

    @Override
    public void run(final Handle handle) {
        super.run(handle);
    }

}
