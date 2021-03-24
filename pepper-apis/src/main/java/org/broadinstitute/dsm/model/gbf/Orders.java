package org.broadinstitute.dsm.model.gbf;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name="Orders")
public class Orders {

    private List<Order> orders = new ArrayList<>();

    public Orders(Order order) {
        orders.add(order);
    }

    public Orders() { }

    @XmlElement(name = "Order")
    public List<Order> getOrders() {
        return orders;
    }

    public void setOrders(List<Order> orders) {
        this.orders = orders;
    }
}
