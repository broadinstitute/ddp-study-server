package org.broadinstitute.ddp.studybuilder.task.osteo;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.studybuilder.task.RevisionStudyActivityVariablesSupportV2;

@Slf4j
public class OsteoParentalConsentV4 extends RevisionStudyActivityVariablesSupportV2 {
    private static final String DATA_FILE = "patches/v4/consent-parental-v4.conf";
    private static final String STUDY_LMS = "CMI-OSTEO";
    private static final String ACTIVITY_CODE = "PARENTAL_CONSENT";

    public OsteoParentalConsentV4() {
        super(STUDY_LMS, ACTIVITY_CODE, DATA_FILE);
    }

}
