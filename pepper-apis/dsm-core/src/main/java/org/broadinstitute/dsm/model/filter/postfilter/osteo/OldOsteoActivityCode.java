package org.broadinstitute.dsm.model.filter.postfilter.osteo;

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

    public static boolean isOldOsteoActivityCode(String activityCode) {
        try {
            OldOsteoActivityCode.valueOf(activityCode);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

}
