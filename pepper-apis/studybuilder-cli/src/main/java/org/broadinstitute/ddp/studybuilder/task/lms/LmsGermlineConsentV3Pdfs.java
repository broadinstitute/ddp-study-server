package org.broadinstitute.ddp.studybuilder.task.lms;

import org.broadinstitute.ddp.studybuilder.task.StudyActivityPdfVersionSupport;

public class LmsGermlineConsentV3Pdfs extends StudyActivityPdfVersionSupport {

    private static final String STUDY_GUID = "cmi-lms";
    private static final String DATA_FILE = "patches/germline-consent-addendum-pdfs-v3.conf";

    public LmsGermlineConsentV3Pdfs() {
        super(STUDY_GUID, DATA_FILE);
    }
}
