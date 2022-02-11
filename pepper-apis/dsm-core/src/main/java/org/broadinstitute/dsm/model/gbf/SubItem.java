package org.broadinstitute.dsm.model.gbf;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import java.util.List;

public class SubItem {

    private String itemNumber;
    private String lotNumber;
    private String serialNumber;
    private String returnTracking;
    private List<Tube> tube;

    public SubItem() {
    }

    public SubItem(String itemNumber, String lotNumber, String serialNumber, String returnTracking, List<Tube> tube) {
        this.itemNumber = itemNumber;
        this.lotNumber = lotNumber;
        this.serialNumber = serialNumber;
        this.returnTracking = returnTracking;
        this.tube = tube;
    }

    @XmlAttribute(name="ItemNumber")
    public String getItemNumber() {
        return itemNumber;
    }

    public void setItemNumber(String itemNumber) {
        this.itemNumber = itemNumber;
    }

    @XmlAttribute(name="LotNumber")
    public String getLotNumber() {
        return lotNumber;
    }

    public void setLotNumber(String lotNumber) {
        this.lotNumber = lotNumber;
    }

    @XmlAttribute(name="SerialNumber")
    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    @XmlElement(name="ReturnTracking")
    public String getReturnTracking() {
        return returnTracking;
    }

    public void setReturnTracking(String returnTracking) {
        this.returnTracking = returnTracking;
    }

    @XmlElement(name="Tube")
    public List<Tube> getTube() {
        return tube;
    }

    public void setTube(List<Tube> tube) {
        this.tube = tube;
    }
}
