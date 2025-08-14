package org.broadinstitute.ddp.studybuilder.task.osteo;

import org.broadinstitute.ddp.studybuilder.task.StudyActivityPdfVersionSupport;

public class OsteoGermlinePedConsentV4Pdf extends StudyActivityPdfVersionSupport {

    private static final String STUDY_GUID = "CMI-OSTEO";
    private static final String DATA_FILE = "patches/v4/germline-ped-consent-addendum-pdfs-v4.conf";

    public OsteoGermlinePedConsentV4Pdf() {
        super(STUDY_GUID, DATA_FILE);
    }
}
