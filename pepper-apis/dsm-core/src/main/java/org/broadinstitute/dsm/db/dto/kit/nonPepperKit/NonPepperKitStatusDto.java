package org.broadinstitute.dsm.db.dto.kit.nonPepperKit;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

import lombok.Getter;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.dsm.model.nonpepperkit.NonPepperStatusKitService;
import org.broadinstitute.dsm.statics.DBConstants;

@Getter
public class NonPepperKitStatusDto {
    String juniperKitId;
    String dsmShippingLabel;
    String participantId;
    String labelDate;
    String labelByEmail;
    String scanDate;
    String scanByEmail;
    String receiveDate;
    String receiveBy;
    String deactivationDate;
    String deactivationByEmail;
    String deactivationReason;
    String trackingNumber;
    String returnTrackingNumber;
    String trackingScanBy;
    Boolean error;
    String errorMessage;
    String discardDate;
    String discardBy;

    public NonPepperKitStatusDto() {}

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
    }

    public static class Builder {

        String juniperKitId;
        String dsmShippingLabel;
        String participantId;
        String labelDate;
        String labelByEmail;
        String scanDate;
        String scanByEmail;
        String receiveDate;
        String receiveBy;
        String deactivationDate;
        String deactivationByEmail;
        String deactivationReason;
        String trackingNumber;
        String returnTrackingNumber;
        String trackingScanBy;
        Boolean error;
        String errorMessage;
        String discardDate;
        String discardBy;


        public Builder() {
        }

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

        public NonPepperKitStatusDto build() {
            return new NonPepperKitStatusDto(this);
        }

    }


}
