package org.broadinstitute.ddp.studybuilder.task.lms;

import org.broadinstitute.ddp.studybuilder.task.StudyActivityPdfVersionSupport;

public class LmsPediatricConsentV3Pdfs extends StudyActivityPdfVersionSupport {

    private static final String STUDY_GUID = "cmi-lms";
    private static final String DATA_FILE = "patches/consent_parental-pdf-v3.conf";

    public LmsPediatricConsentV3Pdfs() {
        super(STUDY_GUID, DATA_FILE);
    }
}

