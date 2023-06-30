package org.broadinstitute.dsm.model.nonpepperkit;

import lombok.Data;

@Data
public class ShipKitRequest {
    JuniperKitRequest juniperKitRequest;
    String kitType;
    String juniperStudyGUID;
}
