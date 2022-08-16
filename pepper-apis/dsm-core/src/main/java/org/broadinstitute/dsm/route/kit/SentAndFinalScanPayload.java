package org.broadinstitute.dsm.route.kit;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
public class SentAndFinalScanPayload extends BaseScanPayload {

    String ddpLabel;

    @Override
    public String getTrackingReturnId() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getDdpLabel() {
        return ddpLabel;
    }
}
