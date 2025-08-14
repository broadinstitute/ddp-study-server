package org.broadinstitute.ddp.studybuilder.task.osteo;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.studybuilder.task.RevisionStudyActivityVariablesSupportV2;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class OsteoGermlinePedConsentV4 extends RevisionStudyActivityVariablesSupportV2 {

    private static final String DATA_FILE = "patches/v4/germline-ped-consent-v4.conf";
    private static final String STUDY_OSTEO = "CMI-OSTEO";
    private static final String ACTIVITY_CODE = "GERMLINE_CONSENT_ADDENDUM_PEDIATRIC";
    private static final String ACTIVITY_NAME = "Learning About Your Child's DNA with Genome Medical and Invitae";
    private static final String ACTIVITY_TITLE =
            "Consent Form Addendum:<br> Learning More About Your Child's DNA with Genome Medical and Invitae";
    private static final String ACTIVITY_TITLE_ES =
            "Anexo al formulario de consentimiento:<br>Aprenda sobre el ADN de su hijo(a) con Genome Medical e Invitae";
    private static final String ACTIVITY_NAME_ES = "Aprenda sobre el ADN de su hijo(a) con Genome Medical e Invitae";
    private static Map<String, String> languageNameMap = new HashMap<>() {{
            put("en", ACTIVITY_NAME);
            put("es", ACTIVITY_NAME_ES);
        }};
    private static Map<String, String> languageTitleMap = new HashMap<>() {{
            put("en", ACTIVITY_TITLE);
            put("es", ACTIVITY_TITLE_ES);
        }};

    public OsteoGermlinePedConsentV4() {
        super(STUDY_OSTEO, ACTIVITY_CODE, DATA_FILE, languageNameMap, languageTitleMap);
    }
}
