package org.broadinstitute.ddp.model.dsm;

import com.google.gson.annotations.SerializedName;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;

import java.util.ArrayList;
import java.util.List;

// All epoch timestamps are expressed in seconds
public class ParticipantStatusTrackingInfo {

    public enum RecordStatus {
        INELIGIBLE, PENDING, SENT, RECEIVED, DELIVERED, UNKNOWN
    }

    @SerializedName("medicalRecords")
    private List<MedicalRecord> medicalRecords;
    @SerializedName("tissue")
    private List<TissueRecord> tissueRecords;
    @SerializedName("kits")
    private List<Kit> kits;
    @SerializedName("workflows")
    private List<ParticipantStatusES.Workflow> workflows;

    private final transient String userGuid;

    private RecordStatus figureOutMedicalRecordStatus(
            EnrollmentStatusType enrollmentStatusType,
            String requested,
            String received
    ) {
        // Is it possible? Should we check for it here?
        if (!enrollmentStatusType.isEnrolled()) {
            return RecordStatus.INELIGIBLE;
        } else if (StringUtils.isBlank(requested) && StringUtils.isBlank(received)) {
            return RecordStatus.PENDING;
        } else if (StringUtils.isNotBlank(requested) && StringUtils.isBlank(received)) {
            return RecordStatus.SENT;
        } else { // received is not blank
            return RecordStatus.RECEIVED;
        }
    }

    public ParticipantStatusTrackingInfo(ParticipantStatusES statusES, EnrollmentStatusType enrollmentStatusType,
                                         String userGuid) {
        this.userGuid = userGuid;
        if (statusES != null) {
            this.workflows = statusES.getWorkflows();
            if (statusES.getSamples() != null) {
                for (ParticipantStatusES.Sample sample : statusES.getSamples()) {
                    if (this.kits == null) {
                        this.kits = new ArrayList<>();
                    }
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

            if (statusES.getMedicalRecords() != null) {
                for (ParticipantStatusES.MedicalRecord medicalRecord : statusES.getMedicalRecords()) {
                    if (this.medicalRecords == null) {
                        this.medicalRecords = new ArrayList<>();
                    }
                    medicalRecords.add(new MedicalRecord(
                            figureOutMedicalRecordStatus(
                                    enrollmentStatusType,
                                    medicalRecord.getRequested(),
                                    medicalRecord.getReceived()
                            ),
                            medicalRecord.getRequested(),
                            medicalRecord.getReceived()
                    ));
                }
            }

            if (statusES.getTissueRecords() != null) {
                for (ParticipantStatusES.TissueRecord tissueRecord : statusES.getTissueRecords()) {
                    if (this.tissueRecords == null) {
                        this.tissueRecords = new ArrayList<>();
                    }
                    this.tissueRecords = new ArrayList<>();
                    tissueRecords.add(new TissueRecord(
                            figureOutMedicalRecordStatus(
                                    enrollmentStatusType,
                                    tissueRecord.getRequested(),
                                    tissueRecord.getReceived()
                            ),
                            tissueRecord.getRequested(),
                            tissueRecord.getReceived()
                    ));
                }
            }
        }
    }

    public String getUserGuid() {
        return userGuid;
    }

    public List<Kit> getKits() {
        return kits;
    }

    public List<MedicalRecord> getMedicalRecords() {
        return medicalRecords;
    }

    public List<TissueRecord> getTissueRecords() {
        return tissueRecords;
    }

    public List<ParticipantStatusES.Workflow> getWorkflows() {
        return workflows;
    }

    public static class Record {
        @SerializedName("status")
        protected RecordStatus status;
        @SerializedName("requestedAt")
        protected String requestedAt;
        @SerializedName("receivedBackAt")
        protected String receivedBackAt;

        protected Record(RecordStatus status, String requestedAt, String receivedBackAt) {
            this.status = status;
            this.requestedAt = requestedAt;
            this.receivedBackAt = receivedBackAt;
        }

        public RecordStatus getStatus() {
            return status;
        }

        public String getRequestedAt() {
            return requestedAt;
        }

        public String getReceivedBackAt() {
            return receivedBackAt;
        }
    }

    public static class MedicalRecord extends Record {
        public MedicalRecord(RecordStatus status, String requestedAt, String receivedBackAt) {
            super(status, requestedAt, receivedBackAt);
        }
    }

    public static class TissueRecord extends Record {
        public TissueRecord(RecordStatus status, String requestedAt, String receivedBackAt) {
            super(status, requestedAt, receivedBackAt);
        }
    }

    @SuppressWarnings("unused")
    public static class Kit {
        @SerializedName("kitType")
        private final String kitType;
        @SerializedName("status")
        private final RecordStatus status;
        // sentAt is NOT NULL since it's guaranteed to be set in Sample by DSM
        @SerializedName("sentAt")
        private final String sentAt;
        @SerializedName("deliveredAt")
        private final String deliveredAt;
        @SerializedName("receivedBackAt")
        private final String receivedBackAt;
        @SerializedName("trackingId")
        private final String trackingId;
        @SerializedName("shipper")
        private final String shipper;

        // We have a constralong that if we receive a kit from DSM, it has been definitely sent
        // As a result, the "sent" is not nullable and we never check its value
        public static RecordStatus figureOutStatus(
                EnrollmentStatusType enrollmentStatusType,
                String delivered,
                String received
        ) {
            // Is it possible? Should we check for it here?
            String entityName = "kit";
            if (!enrollmentStatusType.isEnrolled()) {
                return RecordStatus.INELIGIBLE;
            } else if (StringUtils.isBlank(delivered) && StringUtils.isBlank(received)) {
                return RecordStatus.SENT;
            } else if (StringUtils.isNotBlank(delivered) && StringUtils.isBlank(received)) {
                return RecordStatus.DELIVERED;
            } else { // received != null
                return RecordStatus.RECEIVED;
            }
        }

        public Kit(ParticipantStatusES.Sample sample, RecordStatus status) {
            this.kitType = sample.getKitType();
            this.status = status;
            this.sentAt = sample.getSent();
            this.deliveredAt = sample.getDelivered();
            this.receivedBackAt = sample.getReceived();
            this.trackingId = sample.getTrackingOut();
            this.shipper = sample.getCarrier();
        }

        public RecordStatus getStatus() {
            return status;
        }

        public String getKitType() {
            return kitType;
        }

        public String getSentAt() {
            return sentAt;
        }

        public String getDeliveredAt() {
            return deliveredAt;
        }

        public String getReceivedBackAt() {
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
