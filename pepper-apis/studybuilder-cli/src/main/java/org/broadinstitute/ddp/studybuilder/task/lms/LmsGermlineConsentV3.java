package org.broadinstitute.ddp.studybuilder.task.lms;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.studybuilder.task.RevisionStudyActivityVariablesSupport;

@Slf4j
public class LmsGermlineConsentV3 extends RevisionStudyActivityVariablesSupport {

    private static final String DATA_FILE = "patches/germline-consent-addendum-v3.conf";
    private static final String STUDY_LMS = "cmi-lms";
    private static final String ACTIVITY_CODE = "GERMLINE_CONSENT_ADDENDUM";
    private static final String ACTIVITY_NAME = "Learning About Your DNA with Genome Medical and Invitae";
    private static final String ACTIVITY_TITLE =
            "Consent Form Addendum:<br> Learning More About Your DNA with Genome Medical and Invitae";


    public LmsGermlineConsentV3() {
        super(STUDY_LMS, ACTIVITY_CODE, DATA_FILE, ACTIVITY_NAME, ACTIVITY_TITLE);
    }
}
