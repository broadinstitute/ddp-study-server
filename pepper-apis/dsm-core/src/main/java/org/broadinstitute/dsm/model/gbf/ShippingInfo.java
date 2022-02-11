package org.broadinstitute.dsm.model.gbf;

import javax.xml.bind.annotation.XmlElement;

public class ShippingInfo {

    @XmlElement(name="Account")
    private String account;

    @XmlElement(name="ShipMethod")
    private String shipMethod;

    @XmlElement(name="Address")
    private Address address;

    public ShippingInfo() {
    }

    public ShippingInfo(String account, String shipMethod, Address address) {
        this.account = account;
        this.shipMethod = shipMethod;
        this.address = address;
    }
}
