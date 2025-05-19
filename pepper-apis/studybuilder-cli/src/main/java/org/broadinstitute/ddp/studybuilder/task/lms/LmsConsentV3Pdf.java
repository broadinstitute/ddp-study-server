package org.broadinstitute.ddp.studybuilder.task.lms;

import org.broadinstitute.ddp.studybuilder.task.StudyActivityPdfVersionSupport;

public class LmsConsentV3Pdf extends StudyActivityPdfVersionSupport {

    private static final String STUDY_GUID = "cmi-lms";
    private static final String DATA_FILE = "patches/consent-version3-pdf.conf";

    public LmsConsentV3Pdf() {
        super(STUDY_GUID, DATA_FILE);
    }
}
