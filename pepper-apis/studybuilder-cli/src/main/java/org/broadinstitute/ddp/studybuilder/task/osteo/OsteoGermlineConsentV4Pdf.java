package org.broadinstitute.ddp.studybuilder.task.osteo;

import org.broadinstitute.ddp.studybuilder.task.StudyActivityPdfVersionSupport;

public class OsteoGermlineConsentV4Pdf extends StudyActivityPdfVersionSupport {

    private static final String STUDY_GUID = "CMI-OSTEO";
    private static final String DATA_FILE = "patches/v4/germline-consent-addendum-pdfs-v4.conf";

    public OsteoGermlineConsentV4Pdf() {
        super(STUDY_GUID, DATA_FILE);
    }
}
