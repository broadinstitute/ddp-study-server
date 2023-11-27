package org.broadinstitute.dsm.route.kit;

public class RGPRnaKitFinalScanPayLoad extends SentAndFinalScanPayload{

    String RNA;

    public RGPRnaKitFinalScanPayLoad(String ddpLabel, String kitLabel, String RNA) {
        super(ddpLabel, kitLabel);
        this.RNA = RNA;
    }

    @Override
    public String getTrackingReturnId() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getDdpLabel() {
        return ddpLabel;
    }

    @Override
    public String getHruid()  {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getRNA() {
        return RNA;
    }
}
