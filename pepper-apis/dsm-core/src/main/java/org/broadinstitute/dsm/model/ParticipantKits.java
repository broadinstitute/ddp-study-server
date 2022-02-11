package org.broadinstitute.dsm.model;

import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.KitStatus;

import java.util.List;

public class ParticipantKits {
    String participantId;
    List<KitStatus> samples;

    public ParticipantKits(String participantId, List<KitStatus> samples) {
        this.participantId = participantId;
        this.samples = samples;
    }
}
