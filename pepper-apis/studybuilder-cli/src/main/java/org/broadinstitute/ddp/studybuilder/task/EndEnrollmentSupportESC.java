package org.broadinstitute.ddp.studybuilder.task;

import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Handle;

@Slf4j
public class EndEnrollmentSupportESC extends EndStudyEnrollmentSupport {

    public EndEnrollmentSupportESC() {
        super("cmi-esc", "FOLLOWUPCONSENT");
        log.info("TASK:: EndEnrollmentSupportESC ");
    }

    @Override
    public void run(final Handle handle) {
        super.run(handle);
    }

}
