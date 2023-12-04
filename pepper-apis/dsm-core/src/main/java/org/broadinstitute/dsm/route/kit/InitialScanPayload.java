package org.broadinstitute.dsm.route.kit;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class InitialScanPayload extends BaseScanPayload {

    String hruid;

    public InitialScanPayload(String hruid, String kitLabel) {
        this.hruid = hruid;
        this.kitLabel = kitLabel;
    }

    @Override
    public void setKitLabel(String kitLabel) {
        this.kitLabel = kitLabel;
    }

    @Override
    public String getTrackingReturnId() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getDdpLabel() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getHruid() {
        return hruid;
    }

    @Override
    public void setHruid(String hruid) {
        this.hruid = hruid;
    }

    @Override
    public String getRNA() {
        throw new UnsupportedOperationException();
    }
}
