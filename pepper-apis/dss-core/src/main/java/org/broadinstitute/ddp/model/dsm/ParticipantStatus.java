package org.broadinstitute.ddp.model.dsm;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.SerializedName;

// All epoch timestamps are expresses in seconds
public class ParticipantStatus {
    @SerializedName("mrRequested")
    private Long mrRequested;
    @SerializedName("mrReceived")
    private Long mrReceived;
    @SerializedName("tissueRequested")
    private Long tissueRequested;
    @SerializedName("tissueSent")
    private Long tissueSent;
    @SerializedName("tissueReceived")
    private Long tissueReceived;
    @SerializedName("samples")
    private List<Sample> samples;

    public ParticipantStatus(
            Long mrRequested,
            Long mrReceived,
            Long tissueRequested,
            Long tissueSent,
            Long tissueReceived,
            List<Sample> samples
    ) {
        this.mrRequested = mrRequested;
        this.mrReceived = mrReceived;
        this.tissueRequested = tissueRequested;
        this.tissueSent = tissueSent;
        this.tissueReceived = tissueReceived;
        this.samples = new ArrayList<Sample>(samples);
    }

    public List<Sample> getSamples() {
        return samples;
    }

    public Long getMrRequestedEpochTimeSec() {
        return mrRequested;
    }

    public Long getMrReceivedEpochTimeSec() {
        return mrReceived;
    }

    public Long getTissueRequestedEpochTimeSec() {
        return tissueRequested;
    }

    public Long getTissueSentEpochTimeSec() {
        return tissueSent;
    }

    public Long getTissueReceivedEpochTimeSec() {
        return tissueReceived;
    }

    public static class Sample {
        @SerializedName("kitRequestId")
        private String kitRequestId;
        @SerializedName("kitType")
        private String kitType;
        @SerializedName("sent")
        private long sent;
        @SerializedName("delivered")
        private Long delivered;
        @SerializedName("received")
        private Long received;
        @SerializedName("trackingId")
        private String trackingId;
        @SerializedName("carrier")
        private String carrier;

        public Sample(Sample sourceSample) {
            this.kitRequestId = sourceSample.kitRequestId;
            this.kitType = sourceSample.kitType;
            this.sent = sourceSample.sent;
            this.delivered = sourceSample.delivered;
            this.received = sourceSample.received;
            this.trackingId = sourceSample.trackingId;
            this.carrier = sourceSample.carrier;
        }

        public Sample(String kitRequestId, String kitType, long sent, Long delivered, Long received, String trackingId, String carrier) {
            this.kitRequestId = kitRequestId;
            this.kitType = kitType;
            this.sent = sent;
            this.delivered = delivered;
            this.received = received;
            this.trackingId = trackingId;
            this.carrier = carrier;
        }

        public String getKitRequestId() {
            return kitRequestId;
        }

        public String getKitType() {
            return kitType;
        }

        public long getSentEpochTimeSec() {
            return sent;
        }

        public Long getDeliveredEpochTimeSec() {
            return delivered;
        }

        public Long getReceivedEpochTimeSec() {
            return received;
        }

        public String getTrackingId() {
            return trackingId;
        }

        public String getCarrier() {
            return carrier;
        }
    }
}
