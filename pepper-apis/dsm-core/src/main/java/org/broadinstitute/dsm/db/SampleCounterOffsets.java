package org.broadinstitute.dsm.db;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sample counter offsets control the number at which to start
 * the sample id suffix when generating sample ids
 * for kits.  In the absence of this value, the suffix will
 * start at 0.
 */
public class SampleCounterOffsets {

    private final Map<String, SampleCounterOffset> sampleCounterOffsetsByShortId = new HashMap<>();

    private final Map<String, SampleCounterOffset> sampleCounterOffsetsByCollabParticipantId = new HashMap<>();

    public SampleCounterOffsets(List<SampleCounterOffset> sampleCounterOffsets) {
        for (SampleCounterOffset sampleCounterOffset : sampleCounterOffsets) {
            sampleCounterOffsetsByCollabParticipantId.put(sampleCounterOffset.getCollaboratorSampleId(), sampleCounterOffset);
            sampleCounterOffsetsByShortId.put(sampleCounterOffset.getShortId(), sampleCounterOffset);
        }
    }

    public SampleCounterOffset getKitSummaryByShortId(String shortId) {
        return sampleCounterOffsetsByShortId.get(shortId);
    }

    public SampleCounterOffset getKitSummaryByCollaboratorParticipantId(String collabParticipantId) {
        return sampleCounterOffsetsByCollabParticipantId.get(collabParticipantId);
    }

}
