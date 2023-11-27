package org.broadinstitute.dsm.model.mercury;

import lombok.Data;

@Data
public class MercuryOrderDummyRequest {
    String collaboratorParticipantId;
    String[] kitLabels;
    String realm;
}
