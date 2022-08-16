package org.broadinstitute.dsm.route.kit;

public abstract class BaseScanPayload implements ScanPayload {
    String kitLabel;

    @Override
    public String getKitLabel() {
        return kitLabel;
    }
}
