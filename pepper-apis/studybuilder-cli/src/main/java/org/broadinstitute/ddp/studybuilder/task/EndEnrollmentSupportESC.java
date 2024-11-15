package org.broadinstitute.ddp.studybuilder.task;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EndEnrollmentSupportESC extends EndStudyEnrollmentSupport {

    public EndEnrollmentSupportESC() {
        super("cmi-esc", "FOLLOWUPCONSENT");
        log.info("TASK:: EndEnrollmentSupportESC ");
    }

}
