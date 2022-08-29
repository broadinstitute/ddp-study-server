package org.broadinstitute.dsm.route.kit;

public abstract class BaseScanPayload implements ScanPayload {
    protected String kitLabel;

    @Override
    public String getKitLabel() {
        return kitLabel;
    }
}
