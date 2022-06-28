package org.broadinstitute.dsm.db.dto.mercury;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class ClinicalOrderDto {

    public long orderDate;
    String shortId;
    String sample;
    String orderId;
    String orderStatus;
    long statusDate;
    String statusDetail;
    String sampleType;

}
