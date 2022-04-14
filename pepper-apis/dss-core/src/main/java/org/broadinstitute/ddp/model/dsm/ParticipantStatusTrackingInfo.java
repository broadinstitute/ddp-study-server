package org.broadinstitute.ddp.model.dsm;

import static org.broadinstitute.ddp.util.DateTimeUtils.localDateToEpochSeconds;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.SerializedName;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;

// All epoch timestamps are expressed in seconds
@Slf4j
public class ParticipantStatusTrackingInfo {
    public enum RecordStatus {
        INELIGIBLE, PENDING, SENT, RECEIVED, DELIVERED, UNKNOWN
    }

    @SerializedName("medicalRecord")
    private MedicalRecord medicalRecord;
    @SerializedName("tissueRecord")
    private TissueRecord tissueRecord;
    @SerializedName("kits")
    private List<Kit> kits;
    @SerializedName("workflows")
    private List<ParticipantStatusES.Workflow> workflows;

    private final transient String userGuid;

    private RecordStatus figureOutMedicalRecordStatus(
            String entityName,
            EnrollmentStatusType enrollmentStatusType,
            LocalDate requested,
            LocalDate received
    ) {
        // Is it possible? Should we check for it here?
        if (!enrollmentStatusType.isEnrolled()) {
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
            log.error("Something completely unexpected happened with {}", entityName);
            return RecordStatus.UNKNOWN;
        }
    }

    public ParticipantStatusTrackingInfo(ParticipantStatusES statusES, EnrollmentStatusType enrollmentStatusType,
                                         String userGuid) {
        this.userGuid = userGuid;
        this.workflows = statusES.getWorkflows() != null ? statusES.getWorkflows() : new ArrayList<>();

        LocalDate minReceived = null;
        LocalDate minRequested = null;
        if (statusES.getMedicalRecords() != null) {
            for (ParticipantStatusES.MedicalRecord medicalRecord : statusES.getMedicalRecords()) {
                if (medicalRecord.getReceived() != null) {
                    if (minReceived == null || minReceived.compareTo(medicalRecord.getReceived()) > 0) {
                        minReceived = medicalRecord.getReceived();
                    }
                }
                if (medicalRecord.getRequested() != null) {
                    if (minRequested == null || minRequested.compareTo(medicalRecord.getRequested()) > 0) {
                        minRequested = medicalRecord.getRequested();
                    }
                }
            }
        }
        this.medicalRecord = new MedicalRecord(
                figureOutMedicalRecordStatus(
                        "medical record",
                        enrollmentStatusType,
                        minRequested,
                        minReceived
                ),
                localDateToEpochSeconds(minRequested),
                localDateToEpochSeconds(minReceived)
        );

        minReceived = null;
        minRequested = null;
        if (statusES.getTissueRecords() != null) {
            for (ParticipantStatusES.TissueRecord tissueRecord : statusES.getTissueRecords()) {
                if (tissueRecord.getReceived() != null) {
                    if (minReceived == null || minReceived.compareTo(tissueRecord.getReceived()) > 0) {
                        minReceived = tissueRecord.getReceived();
                    }
                }
                if (tissueRecord.getRequested() != null) {
                    if (minRequested == null || minRequested.compareTo(tissueRecord.getRequested()) > 0) {
                        minRequested = tissueRecord.getRequested();
                    }
                }
            }
        }
        this.tissueRecord = new TissueRecord(
                figureOutMedicalRecordStatus(
                        "tissue record",
                        enrollmentStatusType,
                        minRequested,
                        minReceived
                ),
                localDateToEpochSeconds(minRequested),
                localDateToEpochSeconds(minReceived)
        );

        this.kits = new ArrayList<>();
        if (statusES.getSamples() != null) {
            for (ParticipantStatusES.Sample sample : statusES.getSamples()) {
                kits.add(
                        new Kit(
                                sample,
                                Kit.figureOutStatus(
                                        enrollmentStatusType,
                                        sample.getDelivered(),
                                        sample.getReceived()
                                )
                        )
                );
            }
        }
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

    public List<ParticipantStatusES.Workflow> getWorkflows() {
        return workflows;
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
        private final String kitType;
        @SerializedName("status")
        private final RecordStatus status;
        // sentAt is NOT NULL since it's guaranteed to be set in Sample by DSM
        @SerializedName("sentAt")
        private final long sentAt;
        @SerializedName("deliveredAt")
        private final Long deliveredAt;
        @SerializedName("receivedBackAt")
        private final Long receivedBackAt;
        @SerializedName("trackingId")
        private final String trackingId;
        @SerializedName("shipper")
        private final String shipper;

        // We have a constralong that if we receive a kit from DSM, it has been definitely sent
        // As a result, the "sent" is not nullable and we never check its value
        public static RecordStatus figureOutStatus(
                EnrollmentStatusType enrollmentStatusType,
                LocalDate delivered,
                LocalDate received
        ) {
            // Is it possible? Should we check for it here?
            if (!enrollmentStatusType.isEnrolled()) {
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
                log.error("Something completely unexpected happened with kit");
                return RecordStatus.UNKNOWN;
            }
        }

        public Kit(ParticipantStatusES.Sample sample, RecordStatus status) {
            this.kitType = sample.getKitType();
            this.status = status;
            this.sentAt = localDateToEpochSeconds(sample.getSent());
            this.deliveredAt = localDateToEpochSeconds(sample.getDelivered());
            this.receivedBackAt = localDateToEpochSeconds(sample.getReceived());
            this.trackingId = sample.getTrackingOut();
            this.shipper = sample.getCarrier();
        }

        public RecordStatus getStatus() {
            return status;
        }

        public String getKitType() {
            return kitType;
        }

        public long getSentAt() {
            return sentAt;
        }

        public Long getDeliveredAt() {
            return deliveredAt;
        }

        public Long getReceivedBackAt() {
            return receivedBackAt;
        }

        public String getTrackingId() {
            return trackingId;
        }

        public String getShipper() {
            return shipper;
        }
    }
}
