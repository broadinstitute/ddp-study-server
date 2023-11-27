package org.broadinstitute.dsm.model.ups;

import lombok.Data;

@Data
public class UPSAddress {
    String city;
    String stateProvince;
    String postalCode;
    String countryCode;

    public UPSAddress(String city,
                      String stateProvince,
                      String postalCode,
                      String countryCode) {
        this.city = city;
        this.stateProvince = stateProvince;
        this.postalCode = postalCode;
        this.countryCode = countryCode;
    }
}
