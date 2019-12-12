package org.broadinstitute.ddp.model.user;

import java.util.Arrays;
import java.util.List;

public enum EnrollmentStatusType {
    REGISTERED, ENROLLED, EXITED_BEFORE_ENROLLMENT, EXITED_AFTER_ENROLLMENT;

    public static List<EnrollmentStatusType> getNonExitedStates() {
        return Arrays.asList(REGISTERED, ENROLLED);
    }

    public static List<EnrollmentStatusType> getAllStates() {
        return Arrays.asList(EnrollmentStatusType.class.getEnumConstants());
    }

    public static List<EnrollmentStatusType> getAllExitedStates() {
        return Arrays.asList(EXITED_BEFORE_ENROLLMENT, EXITED_AFTER_ENROLLMENT);
    }
}
