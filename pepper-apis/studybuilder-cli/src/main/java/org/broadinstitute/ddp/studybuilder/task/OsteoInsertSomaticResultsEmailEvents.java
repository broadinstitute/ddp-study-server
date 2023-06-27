package org.broadinstitute.ddp.studybuilder.task;

import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Handle;

@Slf4j
public class OsteoInsertSomaticResultsEmailEvents extends InsertStudyEvents {

    public OsteoInsertSomaticResultsEmailEvents() {
        super("CMI-OSTEO", "patches/somatic-results-email-events.conf");
        log.info("TASK:: OsteoInsertSomaticResultsEmailEvents ");
    }

    @Override
    public void run(final Handle handle) {
        super.run(handle);
    }

}
