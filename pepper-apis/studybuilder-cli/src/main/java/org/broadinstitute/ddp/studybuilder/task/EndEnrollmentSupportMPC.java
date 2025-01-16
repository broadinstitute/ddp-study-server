package org.broadinstitute.ddp.studybuilder.task;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EndEnrollmentSupportMPC extends EndStudyEnrollmentSupport {

    public EndEnrollmentSupportMPC() {
        super("cmi-mpc", "FOLLOWUP");
        log.info("TASK:: EndEnrollmentSupportMPC ");
    }

}
