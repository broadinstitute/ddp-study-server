package org.broadinstitute.ddp.model.dsm;

import com.google.gson.annotations.SerializedName;

import java.util.List;

@SuppressWarnings("unused")
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

        public Workflow(String workflow, String status) {
            this.workflow = workflow;
            this.status = status;
        }

        public String getStatus() {
            return status;
        }

        public String getWorkflow() {
            return workflow;
        }

        public String getDate() {
            return date;
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
        private final String requested;
        @SerializedName("ddpParticipantId")
        private final String ddpParticipantId;
        @SerializedName("name")
        private final String name;
        @SerializedName("received")
        private final String received;
        @SerializedName("type")
        private final String type;

        public MedicalRecord(Long medicalRecordId, String requested, String ddpParticipantId, String name, String received, String type) {
            this.medicalRecordId = medicalRecordId;
            this.requested = requested;
            this.ddpParticipantId = ddpParticipantId;
            this.name = name;
            this.received = received;
            this.type = type;
        }

        public Long getMedicalRecordId() {
            return medicalRecordId;
        }

        public String getRequested() {
            return requested;
        }

        public String getDdpParticipantId() {
            return ddpParticipantId;
        }

        public String getName() {
            return name;
        }

        public String getReceived() {
            return received;
        }

        public String getType() {
            return type;
        }
    }

    public static class TissueRecord {
        @SerializedName("tissueRecordId")
        private final Long tissueRecordId;
        @SerializedName("requested")
        private final String requested;
        @SerializedName("received")
        private final String received;
        @SerializedName("sent")
        private final String sent;
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

        public TissueRecord(Long tissueRecordId, String requested, String received, String sent, String typePX,
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

        public String getRequested() {
            return requested;
        }

        public String getReceived() {
            return received;
        }

        public String getSent() {
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
        private String kitRequestId;
        @SerializedName("kitType")
        private String kitType;
        @SerializedName("sent")
        private String sent;
        @SerializedName("delivered")
        private String delivered;
        @SerializedName("received")
        private String received;
        @SerializedName("trackingIn")
        private String trackingIn;
        @SerializedName("trackingOut")
        private String trackingOut;
        @SerializedName("carrier")
        private String carrier;

        public Sample(String kitRequestId, String kitType, String sent, String delivered, String received,
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

        public String getSent() {
            return sent;
        }

        public String getDelivered() {
            return delivered;
        }

        public String getReceived() {
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
