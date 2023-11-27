package org.broadinstitute.dsm.model;

import java.util.List;

import org.broadinstitute.dsm.db.KitStatus;

public class ParticipantKits {
    String participantId;
    List<KitStatus> samples;

    public ParticipantKits(String participantId, List<KitStatus> samples) {
        this.participantId = participantId;
        this.samples = samples;
    }
}
