package org.broadinstitute.dsm.model.gbf;

import javax.xml.bind.annotation.XmlElement;

public class LineItem {

    @XmlElement(name="ItemNumber")
    private String itemNumber;

    @XmlElement(name="ItemQuantity")
    private String itemQuantity;

    public LineItem() {
    }

    public LineItem(String itemNumber, String itemQuantity) {
        this.itemNumber = itemNumber;
        this.itemQuantity = itemQuantity;
    }
}
