package org.broadinstitute.dsm.service.onchistory;

public interface ParticipantIdProvider {

    /**
     * Given a participant short ID return a participant ID
     */
    int getParticipantIdForShortId(String shortId);
}
