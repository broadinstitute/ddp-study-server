package org.broadinstitute.ddp.model.dsm;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.annotations.SerializedName;

public class ParticipantStatusES {

    @SerializedName("dsm")
    private DSM dsm;
    @SerializedName("samples")
    private final List<Sample> samples;
    @SerializedName("workflows")
    private final List<Workflow> workflows;

    public ParticipantStatusES(List<MedicalRecord> medicalRecords, List<TissueRecord> tissueRecords,
                               List<Sample> samples, List<Workflow> workflows) {
        if (medicalRecords != null || tissueRecords != null) {
            this.dsm = new DSM(medicalRecords, tissueRecords);
        }
        this.samples = samples;
        this.workflows = workflows;
    }

    public List<Sample> getSamples() {
        return samples;
    }

    public List<MedicalRecord> getMedicalRecords() {
        return dsm == null ? null : dsm.getMedicalRecords();
    }

    public List<TissueRecord> getTissueRecords() {
        return dsm == null ? null : dsm.getTissueRecords();
    }

    public List<Workflow> getWorkflows() {
        return workflows;
    }

    public static class Workflow {
        @SerializedName("workflow")
        protected String workflow;
        @SerializedName("status")
        protected String status;
        @SerializedName("date")
        protected String date;
        @SerializedName("data")
        protected Map<String, String> data = new HashMap<>();

        public Workflow(String workflow, String status) {
            this.workflow = workflow;
            this.status = status;
        }

        public String getWorkflow() {
            return workflow;
        }

        public String getStatus() {
            return status;
        }

        public String getDate() {
            return date;
        }

        public Map<String, String> getData() {
            return data;
        }
    }

    public static class DSM {
        @SerializedName("medicalRecords")
        List<MedicalRecord> medicalRecords;
        @SerializedName("tissueRecords")
        List<TissueRecord> tissueRecords;

        public DSM(List<MedicalRecord> medicalRecords, List<TissueRecord> tissueRecords) {
            this.medicalRecords = medicalRecords;
            this.tissueRecords = tissueRecords;
        }

        public List<MedicalRecord> getMedicalRecords() {
            return medicalRecords;
        }

        public List<TissueRecord> getTissueRecords() {
            return tissueRecords;
        }
    }

    public static class MedicalRecord {
        @SerializedName("medicalRecordId")
        private final Long medicalRecordId;
        @SerializedName("requested")
        private final LocalDate requested;
        @SerializedName("received")
        private final LocalDate received;
        @SerializedName("name")
        private final String name;
        @SerializedName("type")
        private final String type;

        public MedicalRecord(Long medicalRecordId, LocalDate requested, String name,
                             LocalDate received, String type) {
            this.medicalRecordId = medicalRecordId;
            this.requested = requested;
            this.name = name;
            this.received = received;
            this.type = type;
        }

        public Long getMedicalRecordId() {
            return medicalRecordId;
        }

        public LocalDate getRequested() {
            return requested;
        }

        public LocalDate getReceived() {
            return received;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }
    }

    public static class TissueRecord {
        @SerializedName("tissueRecordId")
        private final Long tissueRecordId;
        @SerializedName("requested")
        private final LocalDate requested;
        @SerializedName("received")
        private final LocalDate received;
        @SerializedName("sent")
        private final LocalDate sent;
        @SerializedName("typePX")
        private final String typePX;
        @SerializedName("locationPX")
        private final String locationPX;
        @SerializedName("datePX")
        private final String datePX;
        @SerializedName("histology")
        private final String histology;
        @SerializedName("accessionNumber")
        private final String accessionNumber;

        public TissueRecord(Long tissueRecordId, LocalDate requested, LocalDate received, LocalDate sent, String typePX,
                            String locationPX, String datePX, String histology, String accessionNumber) {
            this.tissueRecordId = tissueRecordId;
            this.requested = requested;
            this.received = received;
            this.sent = sent;
            this.typePX = typePX;
            this.locationPX = locationPX;
            this.datePX = datePX;
            this.histology = histology;
            this.accessionNumber = accessionNumber;
        }

        public Long getTissueRecordId() {
            return tissueRecordId;
        }

        public LocalDate getRequested() {
            return requested;
        }

        public LocalDate getReceived() {
            return received;
        }

        public LocalDate getSent() {
            return sent;
        }

        public String getTypePX() {
            return typePX;
        }

        public String getLocationPX() {
            return locationPX;
        }

        public String getDatePX() {
            return datePX;
        }

        public String getHistology() {
            return histology;
        }

        public String getAccessionNumber() {
            return accessionNumber;
        }
    }

    public static class Sample {
        @SerializedName("kitRequestId")
        private final String kitRequestId;
        @SerializedName("kitType")
        private final String kitType;
        @SerializedName("sent")
        private final LocalDate sent;
        @SerializedName("delivered")
        private final LocalDate delivered;
        @SerializedName("received")
        private final LocalDate received;
        @SerializedName("trackingIn")
        private final String trackingIn;
        @SerializedName("trackingOut")
        private final String trackingOut;
        @SerializedName("carrier")
        private final String carrier;

        public Sample(String kitRequestId, String kitType, LocalDate sent, LocalDate delivered, LocalDate received,
                      String trackingIn, String trackingOut, String carrier) {
            this.kitRequestId = kitRequestId;
            this.kitType = kitType;
            this.sent = sent;
            this.delivered = delivered;
            this.received = received;
            this.trackingIn = trackingIn;
            this.trackingOut = trackingOut;
            this.carrier = carrier;
        }

        public String getKitRequestId() {
            return kitRequestId;
        }

        public String getKitType() {
            return kitType;
        }

        public LocalDate getSent() {
            return sent;
        }

        public LocalDate getDelivered() {
            return delivered;
        }

        public LocalDate getReceived() {
            return received;
        }

        public String getTrackingIn() {
            return trackingIn;
        }

        public String getTrackingOut() {
            return trackingOut;
        }

        public String getCarrier() {
            return carrier;
        }
    }
}
