package org.broadinstitute.dsm.model.gbf;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

public class ShippingConfirmation {

    private String orderNumber;
    private String shipper;
    private String shipVia;
    private String shipDate;
    private String clientID;
    private String tracking;
    private Item item;

    public ShippingConfirmation() {
    }

    public ShippingConfirmation(String orderNumber, String shipper, String shipVia, String shipDate, String clientID, String tracking, Item item) {
        this.orderNumber = orderNumber;
        this.shipper = shipper;
        this.shipVia = shipVia;
        this.shipDate = shipDate;
        this.clientID = clientID;
        this.tracking = tracking;
        this.item = item;
    }

    @XmlAttribute(name="OrderNumber")
    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    @XmlAttribute(name="Shipper")
    public String getShipper() {
        return shipper;
    }

    public void setShipper(String shipper) {
        this.shipper = shipper;
    }

    @XmlAttribute(name="ShipVia")
    public String getShipVia() {
        return shipVia;
    }

    public void setShipVia(String shipVia) {
        this.shipVia = shipVia;
    }

    @XmlAttribute(name="ShipDate")
    public String getShipDate() {
        return shipDate;
    }

    public void setShipDate(String shipDate) {
        this.shipDate = shipDate;
    }

    @XmlAttribute(name="ClientID")
    public String getClientID() {
        return clientID;
    }

    public void setClientID(String clientID) {
        this.clientID = clientID;
    }

    @XmlElement(name="Tracking")
    public String getTracking() {
        return tracking;
    }

    public void setTracking(String tracking) {
        this.tracking = tracking;
    }

    @XmlElement(name="Item")
    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
    }
}
