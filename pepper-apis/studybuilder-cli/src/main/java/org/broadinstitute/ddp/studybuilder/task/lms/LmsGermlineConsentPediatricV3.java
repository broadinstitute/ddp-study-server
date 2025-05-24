package org.broadinstitute.ddp.studybuilder.task.lms;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.studybuilder.task.RevisionStudyActivityVariablesSupport;

@Slf4j
public class LmsGermlineConsentPediatricV3 extends RevisionStudyActivityVariablesSupport {

    private static final String DATA_FILE = "patches/germline-consent-addendum-pediatric-v3.conf";
    private static final String STUDY_LMS = "cmi-lms";
    private static final String ACTIVITY_CODE = "GERMLINE_CONSENT_ADDENDUM_PEDIATRIC";
    private static final String ACTIVITY_NAME = "Learning About Your Child’s DNA with Genome Medical and Invitae";
    private static final String ACTIVITY_TITLE =
            "Additional Consent & Assent:<br> Learning More About Your Child's DNA with Genome Medical and Invitae";

    public LmsGermlineConsentPediatricV3() {
        super(STUDY_LMS, ACTIVITY_CODE, DATA_FILE, ACTIVITY_NAME, ACTIVITY_TITLE);
    }


}
