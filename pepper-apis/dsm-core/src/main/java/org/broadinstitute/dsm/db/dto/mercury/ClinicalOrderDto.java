package org.broadinstitute.dsm.db.dto.mercury;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class ClinicalOrderDto {


    String shortId;
    String sample;
    String orderId;
    String orderStatus;
    public long orderDate;
    long statusDate;
    String statusDetail;
    String sampleType;

}
