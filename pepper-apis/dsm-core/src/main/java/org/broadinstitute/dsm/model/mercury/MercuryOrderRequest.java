package org.broadinstitute.dsm.model.mercury;

import lombok.Data;

@Data
public class MercuryOrderRequest {
    String ddpParticipantId;
    String[] kitLabels;
    String realm;
}
