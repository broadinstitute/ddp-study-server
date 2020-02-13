package org.broadinstitute.ddp.model.dsm;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.gson.annotations.SerializedName;

import org.broadinstitute.ddp.model.user.EnrollmentStatusType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// All epoch timestamps are expressed in seconds
public class ParticipantStatusTrackingInfo {

    public enum RecordStatus {
        INELIGIBLE, PENDING, SENT, RECEIVED, DELIVERED, UNKNOWN
    }

    private static final Logger LOG = LoggerFactory.getLogger(ParticipantStatus.class);

    @SerializedName("medicalRecords")
    private MedicalRecord medicalRecord;
    @SerializedName("tissue")
    private TissueRecord tissueRecord;
    @SerializedName("kits")
    private List<Kit> kits;

    private transient String userGuid;

    private RecordStatus figureOutMedicalRecordStatus(
            String userGuid,
            String entityName,
            EnrollmentStatusType enrollmentStatusType,
            Long requested,
            Long received
    ) {
        // Is it possible? Should we check for it here?
        if (enrollmentStatusType != EnrollmentStatusType.ENROLLED) {
            return RecordStatus.INELIGIBLE;
        } else if (requested == null && received == null) {
            return RecordStatus.PENDING;
        } else if (requested != null && received == null) {
            return RecordStatus.SENT;
        } else if (requested != null && received != null) {
            return RecordStatus.RECEIVED;
        } else if (requested == null && received != null) {
            return RecordStatus.RECEIVED;
        } else {
            LOG.error("Something completely unexpected happened with ", entityName);
            return RecordStatus.UNKNOWN;
        }
    }

    public ParticipantStatusTrackingInfo(
            ParticipantStatus dsmParticipantStatus,
            EnrollmentStatusType enrollmentStatusType,
            String userGuid
    ) {
        this.userGuid = userGuid;
        this.medicalRecord = new MedicalRecord(
                figureOutMedicalRecordStatus(
                        userGuid,
                        "medical record",
                        enrollmentStatusType,
                        dsmParticipantStatus.getMrRequestedEpochTimeSec(),
                        dsmParticipantStatus.getMrReceivedEpochTimeSec()
                ),
                dsmParticipantStatus.getMrRequestedEpochTimeSec(),
                dsmParticipantStatus.getMrReceivedEpochTimeSec()
        );
        this.tissueRecord = new TissueRecord(
                figureOutMedicalRecordStatus(
                        userGuid,
                        "tissue record",
                        enrollmentStatusType,
                        dsmParticipantStatus.getTissueRequestedEpochTimeSec(),
                        dsmParticipantStatus.getTissueReceivedEpochTimeSec()
                ),
                dsmParticipantStatus.getTissueRequestedEpochTimeSec(),
                dsmParticipantStatus.getTissueReceivedEpochTimeSec()
        );

        List<ParticipantStatus.Sample> samples = dsmParticipantStatus.getSamples();
        samples = Optional.ofNullable(samples)
                .orElse(new ArrayList<ParticipantStatus.Sample>());

        List<Kit> kits = new ArrayList<Kit>();
        for (ParticipantStatus.Sample sample: samples) {
            kits.add(
                    new Kit(
                            sample,
                            Kit.figureOutStatus(
                                    userGuid,
                                    enrollmentStatusType,
                                    sample.getDeliveredEpochTimeSec(),
                                    sample.getReceivedEpochTimeSec(),
                                    sample.getTrackingId()
                            )
                    )
            );
        }

        this.kits = kits;
    }

    public String getUserGuid() {
        return userGuid;
    }

    public List<Kit> getKits() {
        return kits;
    }

    public MedicalRecord getMedicalRecord() {
        return medicalRecord;
    }

    public TissueRecord getTissueRecord() {
        return tissueRecord;
    }

    public static class Record {
        @SerializedName("status")
        protected RecordStatus status;
        @SerializedName("requestedAt")
        protected Long requestedAt;
        @SerializedName("receivedBackAt")
        protected Long receivedBackAt;

        protected Record(RecordStatus status, Long requestedAt, Long receivedBackAt) {
            this.status = status;
            this.requestedAt = requestedAt;
            this.receivedBackAt = receivedBackAt;
        }

        public RecordStatus getStatus() {
            return status;
        }

        public Long getRequestedAt() {
            return requestedAt;
        }

        public Long getReceivedBackAt() {
            return receivedBackAt;
        }
    }

    public static class MedicalRecord extends Record {
        public MedicalRecord(RecordStatus status, Long requestedAt, Long receivedBackAt) {
            super(status, requestedAt, receivedBackAt);
        }
    }

    public static class TissueRecord extends Record {
        public TissueRecord(RecordStatus status, Long requestedAt, Long receivedBackAt) {
            super(status, requestedAt, receivedBackAt);
        }
    }

    public static class Kit {
        @SerializedName("kitType")
        private String kitType;
        @SerializedName("status")
        private RecordStatus status;
        // sentAt is NOT NULL since it's guaranteed to be set in Sample by DSM
        @SerializedName("sentAt")
        private long sentAt;
        @SerializedName("deliveredAt")
        private Long deliveredAt;
        @SerializedName("receivedBackAt")
        private Long receivedBackAt;
        @SerializedName("trackingId")
        private String trackingId;
        @SerializedName("shipper")
        private String shipper;

        // We have a constralong that if we receive a kit from DSM, it has been definitely sent
        // As a result, the "sent" is not nullable and we never check its value
        public static RecordStatus figureOutStatus(
                String userGuid,
                EnrollmentStatusType enrollmentStatusType,
                Long delivered,
                Long received,
                String trackingId
        ) {
            // Is it possible? Should we check for it here?
            String entityName = "kit";
            if (enrollmentStatusType != EnrollmentStatusType.ENROLLED) {
                return RecordStatus.INELIGIBLE;
            } else if (delivered == null && received == null) {
                return RecordStatus.SENT;
            } else if (delivered != null && received == null) {
                return RecordStatus.DELIVERED;
            } else if (delivered != null && received != null) {
                return RecordStatus.RECEIVED;
            } else if (delivered == null && received != null) {
                return RecordStatus.RECEIVED;
            } else {
                LOG.error("Something completely unexpected happened with ", entityName);
                return RecordStatus.UNKNOWN;
            }
        }

        public Kit(ParticipantStatus.Sample sample, RecordStatus status) {
            this.kitType = sample.getKitType();
            this.status = status;
            this.sentAt = sample.getSentEpochTimeSec();
            this.deliveredAt = sample.getDeliveredEpochTimeSec();
            this.receivedBackAt = sample.getReceivedEpochTimeSec();
            this.trackingId = sample.getTrackingId();
            this.shipper = sample.getCarrier();
        }

        public RecordStatus getStatus() {
            return status;
        }
    }
}
