package org.broadinstitute.ddp.util;

public class IrbAuthResult {
    private boolean ok;

    public IrbAuthResult(boolean passed) {
        this.ok = passed;
    }
}