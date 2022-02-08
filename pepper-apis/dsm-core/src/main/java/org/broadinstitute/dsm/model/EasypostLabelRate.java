package org.broadinstitute.dsm.model;

import lombok.Data;

@Data
public class EasypostLabelRate {

    String express;
    String normal;

    public EasypostLabelRate(String express, String normal) {
        this.express = express;
        this.normal = normal;
    }
}
