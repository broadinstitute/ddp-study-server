package org.broadinstitute.dsm.route.kit;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
public class TrackingScanPayload extends BaseScanPayload {

    String trackingReturnId;

    @Override
    public String getTrackingReturnId() {
        return trackingReturnId;
    }

    @Override
    public String getDdpLabel() {
        throw new UnsupportedOperationException();
    }
}
