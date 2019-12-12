package org.broadinstitute.ddp.model.dsm;

public class DsmCallResponse {
    private ParticipantStatusTrackingInfo trackingInfo;
    private int httpStatus;

    public DsmCallResponse(ParticipantStatusTrackingInfo trackingInfo, int httpStatus) {
        this.trackingInfo = trackingInfo;
        this.httpStatus = httpStatus;
    }

    public ParticipantStatusTrackingInfo getParticipantStatusTrackingInfo() {
        return trackingInfo;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
