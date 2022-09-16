package org.broadinstitute.dsm.model.filter.postfilter.osteo;

import java.util.Arrays;

public enum OldOsteoActivityCode {

    ABOUTCHILD,
    PARENTAL_CONSENT,
    RELEASE_MINOR,
    RELEASE_SELF,
    PREQUAL,
    ABOUT_YOU,
    CONSENT,
    LOVEDONE,
    CONSENT_ASSENT;

    public static String[] asStrings() {
        return Arrays.stream(OldOsteoActivityCode.values())
                .map(OldOsteoActivityCode::toString)
                .toArray(String[]::new);
    }

}
