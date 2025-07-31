package org.broadinstitute.ddp.studybuilder.task.osteo;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.studybuilder.task.RevisionStudyActivityVariablesSupportV2;

@Slf4j
public class OsteoConsentAssentV4 extends RevisionStudyActivityVariablesSupportV2 {
    private static final String DATA_FILE = "patches/v4/consent-assent-v4.conf";
    private static final String STUDY_LMS = "CMI-OSTEO";
    private static final String ACTIVITY_CODE = "CONSENT_ASSENT";

    public OsteoConsentAssentV4() {
        super(STUDY_LMS, ACTIVITY_CODE, DATA_FILE);
    }

}
