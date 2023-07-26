package org.broadinstitute.dsm.model.nonpepperkit;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class NonPepperKitStatus {
    String juniperKitId;
    String dsmShippingLabel;
    String participantId;
    String labelDate;
    String labelByEmail;
    String scanDate;
    String scanByEmail;
    String receiveDate;
    String receiveByEmail;
    String deactivationDate;
    String deactivationByEmail;
    String deactivationReson;
    String trackingNumber;
    String returnTrackingNumber;
    String trackingScanBy;
    Boolean error;
    String errorMessage;
    String discardDate;
    String discardBy;
}
