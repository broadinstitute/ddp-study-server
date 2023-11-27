package org.broadinstitute.dsm.db.dto.mercury;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class MercurySampleDto {
    String sampleType;
    String sample;
    String sampleStatus;
    String collectionDate;
    long sequencingOrderDate;
    Long tissueId;
    Long dsmKitRequestId;
    String sequencingRestriction;
    String lastStatus;
    String lastOrderNumber;
    String pdoOrderId;
}
