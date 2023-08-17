package org.broadinstitute.dsm.model;

import org.broadinstitute.dsm.exception.DsmInternalError;

public enum Study {
    ATCP("ATCP"),
    RGP("RGP"),
    SINGULAR("SINGULAR"),
    LMS("CMI-LMS"),
    CMI_OSTEO("CMI-OSTEO");

    private String value;

    Study(String value) {
        this.value = value;
    }

    public static Study of(String studyGuid) {
        for (Study study : Study.values()) {
            if (study.value.equalsIgnoreCase(studyGuid)) {
                return study;
            }
        }
        throw new DsmInternalError("Study: ".concat(studyGuid).concat(" does not exist"));
    }
}
