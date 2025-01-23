package org.broadinstitute.ddp.studybuilder.task;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EndEnrollmentSupportMBC extends EndStudyEnrollmentSupport {

    public EndEnrollmentSupportMBC() {
        super("cmi-mbc");
        log.info("TASK:: EndEnrollmentSupportMBC ");
    }

}
