package org.broadinstitute.dsm.model.bsp;

import lombok.Data;

@Data
public class BSPKitRegistration {

    public String barcode;
    public boolean dsmKit;

    public BSPKitRegistration(String barcode) {
        this.barcode = barcode;
        this.dsmKit = false;
    }
}
