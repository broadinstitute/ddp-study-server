package org.broadinstitute.dsm.service.participantdata;

public interface FamilyIdProvider {

    /**
     * Create a unique family ID for participant
     */
    long createFamilyId(String ddpParticipantId);
}
