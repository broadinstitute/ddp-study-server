package org.broadinstitute.dsm.model.gbf;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;

@XmlRootElement(name="ShippingConfirmations")
public class ShippingConfirmations {

    private List<ShippingConfirmation> shippingConfirmations;

    @XmlElement(name = "ShippingConfirmation")
    public List<ShippingConfirmation> getShippingConfirmations() {
        return shippingConfirmations;
    }

    public void setShippingConfirmations(List<ShippingConfirmation> shippingConfirmations) {
        this.shippingConfirmations = shippingConfirmations;
    }
}
