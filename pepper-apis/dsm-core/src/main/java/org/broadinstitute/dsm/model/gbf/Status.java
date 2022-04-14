package org.broadinstitute.dsm.model.gbf;

import lombok.Data;

@Data
public class Status {

    private String orderNumber;
    private String orderStatus;

    public Status(String orderNumber, String orderStatus) {
        this.orderNumber = orderNumber;
        this.orderStatus = orderStatus;
    }
}
