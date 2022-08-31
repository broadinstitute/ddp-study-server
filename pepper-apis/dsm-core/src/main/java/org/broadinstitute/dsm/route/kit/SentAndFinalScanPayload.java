package org.broadinstitute.dsm.route.kit;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class SentAndFinalScanPayload extends BaseScanPayload {

    String ddpLabel;

    public SentAndFinalScanPayload(String ddpLabel, String kitLabel) {
        this.ddpLabel = ddpLabel;
        this.kitLabel = kitLabel;
    }

    @Override
    public String getTrackingReturnId() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getDdpLabel() {
        return ddpLabel;
    }
}
