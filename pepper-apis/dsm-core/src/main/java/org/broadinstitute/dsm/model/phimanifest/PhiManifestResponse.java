package org.broadinstitute.dsm.model.phimanifest;

import lombok.Setter;

@Setter
public class PhiManifestResponse {
    String[][] data;
    String ddpParticipantId;
    String orderId;
}
