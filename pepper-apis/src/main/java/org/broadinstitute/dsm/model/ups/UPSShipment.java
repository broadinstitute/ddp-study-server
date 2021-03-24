package org.broadinstitute.dsm.model.ups;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class UPSShipment {
    @SerializedName ("package")
    UPSPackage[] upsPackageArray;
}

class UPSLocation {
    //address is here but I don't think we care about that
}

