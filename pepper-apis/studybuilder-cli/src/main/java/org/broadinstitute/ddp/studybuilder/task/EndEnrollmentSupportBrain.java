package org.broadinstitute.ddp.studybuilder.task;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EndEnrollmentSupportBrain extends EndStudyEnrollmentSupport {

    public EndEnrollmentSupportBrain() {
        super("cmi-brain");
        log.info("TASK:: EndEnrollmentSupportBrain ");
    }

}
