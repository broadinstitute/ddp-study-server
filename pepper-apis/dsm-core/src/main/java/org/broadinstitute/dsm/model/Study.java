package org.broadinstitute.dsm.model;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.exception.DsmInternalError;

public enum Study {
    ATCP("ATCP"),
    RGP("RGP"),
    SINGULAR("SINGULAR"),
    LMS("LMS"),
    OSTEO2("OSTEO2"),
    CMI_OSTEO("CMI-OSTEO");

    private static final List<Study> PE_CGS = Arrays.asList(LMS, OSTEO2);
    private static final String CMI_PREFIX = "cmi-";
    private String value;

    Study(String value) {
        this.value = value;
    }

    public static boolean isPECGS(String instanceName) {
        instanceName = instanceName
                .replace(CMI_PREFIX, StringUtils.EMPTY)
                .toUpperCase();

        try {
            Study study = valueOf(instanceName);
            return PE_CGS.contains(study);
        } catch (IllegalArgumentException iae) {
            return false;
        }

    }

    public static Study of(String studyGuid) {
        for (Study study : Study.values()) {
            if (study.value.equals(studyGuid)) {
                return study;
            }
        }
        throw new DsmInternalError("Study: ".concat(studyGuid).concat(" does not exist"));
    }
}
