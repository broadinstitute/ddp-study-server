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

    // key is the kit type id, value is the sample counter offset for that kit type
    private Map<Integer, Integer> sampleCounterBySampleTypeId = new HashMap<>();

    public SampleCounterOffset(String shortId, String legacyCollaboratorParticipantId, Map<Integer, Integer> numKitsByType) {
        this.shortId = shortId;
        this.legacyCollaboratorParticipantId = legacyCollaboratorParticipantId;
        this.sampleCounterBySampleTypeId = numKitsByType;
    }

    public int getSampleCounterOffsetForKitTypeId(int kitTypeId) {
        return sampleCounterBySampleTypeId.getOrDefault(kitTypeId, 0);
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

    public void setSampleCounterOffsetForKitTypeId(Integer kitTypeId, Integer offset) {
        sampleCounterBySampleTypeId.put(kitTypeId, offset);
    }
}
