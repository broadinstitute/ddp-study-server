package org.broadinstitute.dsm.db;

import java.util.HashMap;
import java.util.Map;

/**
 * Each kit type can have its own offset for a participant.  DSM's collaborator participant
 * naming scheme follows a general pattern of:
 * ddp_instance.collaborator_id_prefix + [participant counter | participant short code] + kit_type.kit_type_name + sample count
 * where "sample count" is a count of previous samples received for a given participant and kit type, and "participant counter"
 * is a counter embedded in special case code for some studies whose kit handling predates DSM (such as A-T)
 * The sample count is the sum of this sample counter offset and the count of all kits
 * found in DSM's kit tables for the given participant.
 * In addition, the offset can declare a collaboratorParticipantId, which can be used
 * instead of the prefix + hruid based naming for participants whose
 * kits predate DSM.
 */
public class SampleCounterOffset {

    private String collaboratorParticipantId;

    private String shortId;

    // key is the kit type id, value is the sample counter offset for that kit type
    private Map<Integer, Integer> offestsByKitType = new HashMap<>();

    public SampleCounterOffset(String shortId, String collaboratorParticipantId, Map<Integer, Integer> offestsByKitType) {
        this.shortId = shortId;
        this.collaboratorParticipantId = collaboratorParticipantId;
        this.offestsByKitType = offestsByKitType;
    }

    public int getSampleCounterOffsetForKitTypeId(int kitTypeId) {
        return offestsByKitType.getOrDefault(kitTypeId, 0);
    }

    /**
     * If set, this is the collaborator participant id to use
     * for kits.
     */
    public String getCollaboratorParticipantIdId() {
        return collaboratorParticipantId;
    }

    public String getShortId() {
        return shortId;
    }

    public void setSampleCounterOffsetForKitTypeId(Integer kitTypeId, Integer offset) {
        offestsByKitType.put(kitTypeId, offset);
    }
}
