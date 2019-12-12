package org.broadinstitute.ddp.service;

public class ActivityInstanceCreationValidation {

    private final boolean hasTooManyInstances;

    private final boolean hasUnsatisfiedPrecondition;

    public ActivityInstanceCreationValidation(boolean hasTooManyInstances, boolean hasUnsatisfiedPrecondition) {
        this.hasTooManyInstances = hasTooManyInstances;
        this.hasUnsatisfiedPrecondition = hasUnsatisfiedPrecondition;
    }

    public boolean hasTooManyInstances() {
        return hasTooManyInstances;
    }

    public boolean hasUnsatisfiedPrecondition() {
        return hasUnsatisfiedPrecondition;
    }
}
