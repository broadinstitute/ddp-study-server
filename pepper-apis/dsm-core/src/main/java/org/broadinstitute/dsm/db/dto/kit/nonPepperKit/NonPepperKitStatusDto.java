package org.broadinstitute.dsm.db.dto.kit.nonPepperKit;

import lombok.Getter;

/**
* Represents the various statuses a kit can take in their time in DSM.
 * From being new to being received. Each of the fields respond to
 * one of the kit's variables
* **/
@Getter
public class NonPepperKitStatusDto {
    Boolean error;
    private String juniperKitId;
    private String dsmShippingLabel;
    private String participantId;
    private String labelDate;
    private String labelByEmail;
    private String mfBarcode;
    private String scanDate;
    private String scanByEmail;
    private String receiveDate;
    private String receiveBy;
    private String deactivationDate;
    private String deactivationByEmail;
    private String deactivationReason;
    private String trackingNumber;
    private String returnTrackingNumber;
    private String trackingScanBy;
    private String errorMessage;
    private String discardDate;
    private String discardBy;
    private String currentStatus;
    private String collaboratorParticipantId;
    private String collaboratorSampleId;

    /**
     * Creates a NonPepperKitStatusDto from a builder
     * */
    public NonPepperKitStatusDto(Builder builder) {
        this.juniperKitId = builder.juniperKitId;
        this.dsmShippingLabel = builder.dsmShippingLabel;
        this.participantId = builder.participantId;
        this.labelDate = builder.labelDate;
        this.labelByEmail = builder.labelByEmail;
        this.scanDate = builder.scanDate;
        this.scanByEmail = builder.scanByEmail;
        this.receiveDate = builder.receiveDate;
        this.receiveBy = builder.receiveBy;
        this.deactivationDate = builder.deactivationDate;
        this.deactivationByEmail = builder.deactivationByEmail;
        this.deactivationReason = builder.deactivationReason;
        this.trackingNumber = builder.trackingNumber;
        this.returnTrackingNumber = builder.returnTrackingNumber;
        this.trackingScanBy = builder.trackingScanBy;
        this.error = builder.error;
        this.errorMessage = builder.errorMessage;
        this.discardDate = builder.discardDate;
        this.discardBy = builder.discardBy;
        this.currentStatus = builder.currentStatus;
        this.collaboratorParticipantId = builder.collaboratorParticipantId;
        this.collaboratorSampleId = builder.collaboratorSampleId;
        this.mfBarcode = builder.mfBarcode;
    }

    public static class Builder {

        private Boolean error;
        private String juniperKitId;
        private String dsmShippingLabel;
        private String participantId;
        private String labelDate;
        private String labelByEmail;
        private String mfBarcode;
        private String scanDate;
        private String scanByEmail;
        private String receiveDate;
        private String receiveBy;
        private String deactivationDate;
        private String deactivationByEmail;
        private String deactivationReason;
        private String trackingNumber;
        private String returnTrackingNumber;
        private String trackingScanBy;
        private String errorMessage;
        private String discardDate;
        private String discardBy;
        private String currentStatus;
        private String collaboratorParticipantId;
        private String collaboratorSampleId;

        public Builder withJuniperKitId(String juniperKitId) {
            this.juniperKitId = juniperKitId;
            return this;
        }

        public Builder withDsmShippingLabel(String dsmShippingLabel) {
            this.dsmShippingLabel = dsmShippingLabel;
            return this;
        }

        public Builder withParticipantId(String participantId) {
            this.participantId = participantId;
            return this;
        }

        public Builder withLabelDate(String labelDate) {
            this.labelDate = labelDate;
            return this;
        }

        public Builder withLabelByEmail(String labelByEmail) {
            this.labelByEmail = labelByEmail;
            return this;
        }
        public Builder withMfBarcode(String mfBarcode) {
            this.mfBarcode = mfBarcode;
            return this;
        }

        public Builder withScanDate(String scanDate) {
            this.scanDate = scanDate;
            return this;
        }

        public Builder withScanByEmail(String scanByEmail) {
            this.scanByEmail = scanByEmail;
            return this;
        }

        public Builder withReceiveDate(String receiveDate) {
            this.receiveDate = receiveDate;
            return this;
        }

        public Builder withReceiveBy(String receiveBy) {
            this.receiveBy = receiveBy;
            return this;
        }

        public Builder withDeactivationDate(String deactivationDate) {
            this.deactivationDate = deactivationDate;
            return this;
        }

        public Builder withDeactivationByEmail(String deactivationByEmail) {
            this.deactivationByEmail = deactivationByEmail;
            return this;
        }

        public Builder withDeactivationReason(String deactivationReason) {
            this.deactivationReason = deactivationReason;
            return this;
        }

        public Builder withTrackingNumber(String trackingNumber) {
            this.trackingNumber = trackingNumber;
            return this;
        }

        public Builder withReturnTrackingNumber(String returnTrackingNumber) {
            this.returnTrackingNumber = returnTrackingNumber;
            return this;
        }

        public Builder withTrackingScanBy(String trackingScanBy) {
            this.trackingScanBy = trackingScanBy;
            return this;
        }

        public Builder withError(Boolean error) {
            this.error = error;
            return this;
        }

        public Builder withErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder withDiscardDate(String discardDate) {
            this.discardDate = discardDate;
            return this;
        }

        public Builder withDiscardBy(String discardBy) {
            this.discardBy = discardBy;
            return this;
        }

        public Builder withCurrentStatus(String currentStatus) {
            this.currentStatus = currentStatus;
            return this;
        }

        public Builder withCollaboratorParticipantId(String collaboratorParticipantId) {
            this.collaboratorParticipantId = collaboratorParticipantId;
            return this;
        }

        public Builder withCollaboratorSampleId(String collaboratorSampleId) {
            this.collaboratorSampleId = collaboratorSampleId;
            return this;
        }

        public NonPepperKitStatusDto build() {
            return new NonPepperKitStatusDto(this);
        }

    }


}
