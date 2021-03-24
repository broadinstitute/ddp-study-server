package org.broadinstitute.dsm.model.gbf;

import javax.xml.bind.annotation.XmlElement;
import java.util.List;

public class Order {

    @XmlElement(name="OrderNumber")
    private String orderNumber;

    @XmlElement(name="ClientAccount")
    private String clientAccount;

    @XmlElement(name="PatientMRN")
    private String patientMRN;

    @XmlElement(name="ShippingInfo")
    private ShippingInfo shippingInfo;

    @XmlElement(name="LineItem")
    private List<LineItem> lineItem;

    public Order() {
    }

    public Order(String orderNumber, String clientAccount, String patientMRN, ShippingInfo shippingInfo, List<LineItem> lineItem) {
        this.orderNumber = orderNumber;
        this.clientAccount = clientAccount;
        this.patientMRN = patientMRN;
        this.shippingInfo = shippingInfo;
        this.lineItem = lineItem;
    }
}
