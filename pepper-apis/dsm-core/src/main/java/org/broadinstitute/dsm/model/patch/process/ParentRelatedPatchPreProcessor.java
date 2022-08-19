
package org.broadinstitute.dsm.model.patch.process;

import java.util.function.Function;

import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDao;
import org.broadinstitute.dsm.model.patch.Patch;
import org.broadinstitute.dsm.model.patch.process.exception.PatchProcessingException;

// ParentRelatedPatchPreProcessor makes sure that patch.parent and patch.parentId are set to correct values.
// It must be done in order for the patch request to exercise correct operations in DB and ES.
// Correct value for patch.parent is "participantId" and for patch.parentId the correct value is read from DB.
public class ParentRelatedPatchPreProcessor extends BasePatchPreProcessor {

    private ParticipantDao participantDao;

    public ParentRelatedPatchPreProcessor(ParticipantDao participantDao) {
        this.participantDao = participantDao;
    }

    @Override
    protected final Patch updatePatch() {
        return fetchDependenciesAndThenUpdatePatch();
    }

    private Patch fetchDependenciesAndThenUpdatePatch() {
        return fetchDdpInstanceId
                .andThen(fetchParticipantId)
                .andThen(updatePatchParentAndParentId)
                .apply(originalPatch.getRealm());
    }

    private final Function<String, Integer> fetchDdpInstanceId = this::getDdpInstanceIdAsInt;

    // extracted as an instance method for testing purposes
    protected int getDdpInstanceIdAsInt(String realm) {
        return DDPInstance.getDDPInstance(realm).getDdpInstanceIdAsInt();
    }

    private final Function<Integer, String> fetchParticipantId = ddpInstanceId -> participantDao
            .getParticipantIdByGuidAndDdpInstanceId(ddpParticipantId, ddpInstanceId)
            .map(Object::toString)
            .orElseThrow(() -> new PatchProcessingException("Could not process the patch"));

    private final Function<String, Patch> updatePatchParentAndParentId = participantId -> {
        Patch updatedPatch = originalPatch.clone();
        updatedPatch.setParent(PARTICIPANT_ID);
        updatedPatch.setParentId(participantId);
        return updatedPatch;
    };

}
