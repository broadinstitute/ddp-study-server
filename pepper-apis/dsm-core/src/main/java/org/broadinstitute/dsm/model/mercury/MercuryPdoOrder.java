package org.broadinstitute.dsm.model.mercury;

import lombok.AllArgsConstructor;

public class MercuryPdoOrder {
    public String creatorID;
    public String orderID;
    public String researchProject;
    public KitBarcode[] pdoSamples;

    public MercuryPdoOrder(String creatorID, String orderID, String researchProject, String[] kitBarcodes) {
        this.creatorID = creatorID;
        this.orderID = orderID;
        this.researchProject = researchProject;
        this.pdoSamples = new KitBarcode[kitBarcodes.length];
        for (int i = 0; i < kitBarcodes.length; i++) {
            pdoSamples[i] = new KitBarcode(kitBarcodes[i]);
        }
    }

    @AllArgsConstructor
    class KitBarcode {
        public String kitBarcode;
    }
}
