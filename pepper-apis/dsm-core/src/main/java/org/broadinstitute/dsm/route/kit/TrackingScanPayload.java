package org.broadinstitute.dsm.route.kit;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
public class TrackingScanPayload extends BaseScanPayload {

    String trackingReturnId;

    @Override
    public void setKitLabel(String kitLabel) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getTrackingReturnId() {
        return trackingReturnId;
    }

    @Override
    public String getDdpLabel() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getHruid() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setHruid(String hruid) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getRNA() {
        throw new UnsupportedOperationException();
    }

}
