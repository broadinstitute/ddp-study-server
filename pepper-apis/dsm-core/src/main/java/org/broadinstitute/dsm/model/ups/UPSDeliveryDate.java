package org.broadinstitute.dsm.model.ups;

import lombok.Data;

@Data
public class UPSDeliveryDate {
    public String date;
    public String upsPackageId;
    public String type;

    public UPSDeliveryDate(String date, String upsPackageId, String type) {
        this.date = date;
        this.upsPackageId = upsPackageId;
        this.type = type;
    }
}
