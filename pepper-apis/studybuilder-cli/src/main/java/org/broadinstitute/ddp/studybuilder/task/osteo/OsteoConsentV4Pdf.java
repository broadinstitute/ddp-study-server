package org.broadinstitute.ddp.studybuilder.task.osteo;

import org.broadinstitute.ddp.studybuilder.task.StudyActivityPdfVersionSupport;

public class OsteoConsentV4Pdf extends StudyActivityPdfVersionSupport {

    private static final String STUDY_GUID = "CMI-OSTEO";
    private static final String DATA_FILE = "patches/v4/consent-pdfs-v4.conf";

    public OsteoConsentV4Pdf() {
        super(STUDY_GUID, DATA_FILE);
    }
}
