
package org.broadinstitute.dsm.model.patch;

import java.util.function.Function;

import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDao;

// ParentRelatedPatchPreProcessor makes sure that patch.parent and patch.parentId are set to correct values.
// It must be done in order for the patch request to exercise correct operations in DB and ES.
// Correct value for patch.parent is "participantId" and for patch.parentId the correct value is read from DB.
public class ParentRelatedPatchPreProcessor extends BasePatchPreProcessor {

    private static final String DDP_PARTICIPANT_ID = "ddpParticipantId";
    private static final String PARTICIPANT_ID     = "participantId";

    private ParticipantDao participantDao;

    public ParentRelatedPatchPreProcessor(ParticipantDao participantDao) {
        this.participantDao = participantDao;
    }

    @Override
    protected final Patch updatePatchIfRequired() {
        Patch maybeUpdatedResult = originalPatch;
        if (parentEqualsGuid(originalPatch)) {
            maybeUpdatedResult = fetchDdpInstanceId
                    .andThen(fetchParticipantId)
                    .andThen(updatePatch)
                    .apply(originalPatch.getRealm());
        }
        return maybeUpdatedResult;
    }

    private final Function<String, Integer> fetchDdpInstanceId = this::getDdpInstanceIdAsInt;

    private final Function<Integer, String> fetchParticipantId = ddpInstanceId -> participantDao
            .getParticipantIdByGuidAndDdpInstanceId(ddpParticipantId, ddpInstanceId)
            .map(Object::toString)
            .orElseThrow(() -> new PatchProcessingException("Could not process the patch"));

    private final Function<String, Patch> updatePatch = participantId -> {
        Patch updatedPatch = originalPatch.clone();
        updatedPatch.setParent(PARTICIPANT_ID);
        updatedPatch.setParentId(participantId);
        return updatedPatch;
    };

    // extracted as an instance method for testing purposes
    int getDdpInstanceIdAsInt(String realm) {
        return DDPInstance.getDDPInstance(realm).getDdpInstanceIdAsInt();
    }

    private static boolean parentEqualsGuid(Patch patch) {
        return DDP_PARTICIPANT_ID.equals(patch.getParent());
    }

    private static class PatchProcessingException extends RuntimeException {

        public PatchProcessingException(String message) {
            super(message);
        }
    }

}
