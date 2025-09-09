package org.broadinstitute.dsm.db;

import java.util.HashMap;
import java.util.Map;

/**
 * Each kit type can have its own offset for a participant.  In addition,
 * the offset can declare a collaboratorParticipantId, which can be used
 * instead of the prefix + hruid based naming for participants whose
 * kits predate DSM.
 */
public class SampleCounterOffset {

    private String legacyCollaboratorParticipantId;

    private String shortId;

    private Map<Integer, Integer> numKitsByType = new HashMap<>();

    public SampleCounterOffset(String shortId, String legacyCollaboratorParticipantId, Map<Integer, Integer> numKitsByType) {
        this.shortId = shortId;
        this.legacyCollaboratorParticipantId = legacyCollaboratorParticipantId;
        this.numKitsByType = numKitsByType;
    }

    public int getNumberOfKitsForKitTypeId(int kitTypeId) {
        return numKitsByType.getOrDefault(kitTypeId, 0);
    }

    /**
     * If set, this is the collaborator participant id to use
     * for kits.
     */
    public String getLegacyCollaboratorParticipantId() {
        return legacyCollaboratorParticipantId;
    }

    public String getShortId() {
        return shortId;
    }
}
