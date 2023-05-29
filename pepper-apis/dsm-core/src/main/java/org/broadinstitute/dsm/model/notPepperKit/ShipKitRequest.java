package org.broadinstitute.dsm.model.notPepperKit;

import lombok.Data;

@Data
public class ShipKitRequest {
    JuniperKitRequest juniperKitRequest;
    String kitType;
    String juniperStudyGUID;
}
