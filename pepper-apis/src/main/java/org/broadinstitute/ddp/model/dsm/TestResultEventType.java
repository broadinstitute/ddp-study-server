package org.broadinstitute.ddp.model.dsm;

public enum TestResultEventType {
    NEGATIVE, POSITIVE, OTHER, ANY;

    public static TestResultEventType categoryOf(String result) {
        result = result.toUpperCase();
        if (NEGATIVE.name().equals(result) || "NEG".equals(result)) {
            return NEGATIVE;
        } else if (POSITIVE.name().equals(result) || "POS".equals(result)) {
            return POSITIVE;
        } else {
            return OTHER;
        }
    }

    public boolean matches(String result) {
        return this == ANY || this == categoryOf(result);
    }
}
