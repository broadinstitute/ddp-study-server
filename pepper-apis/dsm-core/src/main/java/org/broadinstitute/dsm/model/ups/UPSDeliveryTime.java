package org.broadinstitute.dsm.model.ups;

import lombok.Data;

@Data
public
class UPSDeliveryTime {
    String startTime;
    String endTime;
    String type;
    String upsPackageId;

    public UPSDeliveryTime(String startTime, String endTime, String upsPackageId, String type) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.upsPackageId = upsPackageId;
        this.type = type;
    }

}
