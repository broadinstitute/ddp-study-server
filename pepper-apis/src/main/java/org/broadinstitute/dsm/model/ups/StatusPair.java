package org.broadinstitute.dsm.model.ups;

import lombok.Data;

@Data
public class StatusPair {
    String type;
    String trackingId;

    public StatusPair(String type, String trackingId){
        this.type = type;
        this.trackingId = trackingId;
    }
}

