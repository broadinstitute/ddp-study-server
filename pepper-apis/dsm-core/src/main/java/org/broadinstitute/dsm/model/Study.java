package org.broadinstitute.dsm.model;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

public enum Study {
    ATCP,
    RGP,
    SINGULAR,
    LMS,
    OSTEO2;

    private static final List<Study> PE_CGS = Arrays.asList(LMS, OSTEO2);
    private static final String CMI_PREFIX = "cmi-";

    public static boolean isPECGS(String instanceName) {
        instanceName = instanceName
                .replace(CMI_PREFIX, StringUtils.EMPTY)
                .toUpperCase();

        Study study = valueOf(instanceName);
        return PE_CGS.contains(study);
    }
}
