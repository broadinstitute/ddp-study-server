package org.broadinstitute.dsm.model.mercury;

import lombok.Data;

@Data
public class MercuryStatusMessage {
    String orderID;
    String orderStatus;
    String details;
    String pdoKey;
    String json;
}
