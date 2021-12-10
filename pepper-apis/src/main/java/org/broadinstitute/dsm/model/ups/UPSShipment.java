package org.broadinstitute.dsm.model.ups;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class UPSShipment {
    @SerializedName ("package")
    UPSPackage[] upsPackageArray;
    String dsmKitRequestId;
    String upsShipmentId;

    public UPSShipment(UPSPackage upsPackage, String dsmKitRequestId, String upsShipmentId) {
        this.upsPackageArray = new UPSPackage[] { upsPackage };
        this.dsmKitRequestId = dsmKitRequestId;
        this.upsShipmentId = upsShipmentId;
    }
}

class UPSAddress{
    String city;
    String stateProvince;
    String postalCode;
    String countryCode;

    public UPSAddress(String city,
            String stateProvince,
            String postalCode,
            String countryCode){
        this.city = city;
        this.stateProvince = stateProvince;
        this.postalCode = postalCode;
        this.countryCode=countryCode;
    }
}

