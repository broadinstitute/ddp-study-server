package org.broadinstitute.ddp.studybuilder.task.lms;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.studybuilder.task.RevisionStudyActivityVariablesSupport;

@Slf4j
public class LmsConsentVersion3 extends RevisionStudyActivityVariablesSupport {
    private static final String DATA_FILE = "patches/consent-version-3.conf";
    private static final String STUDY_LMS = "cmi-lms";
    private static final String ACTIVITY_CODE = "CONSENT";

    public LmsConsentVersion3() {
        super(STUDY_LMS, ACTIVITY_CODE, DATA_FILE);
    }

}
