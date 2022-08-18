package org.broadinstitute.dsm.model.patch;

import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDaoImpl;
import org.broadinstitute.dsm.statics.DBConstants;

// A base class for processing and transforming Patch instance.
// A child class must implement updatePatchIfRequired();
public abstract class BasePatchPreProcessor implements PreProcessor<Patch> {

    protected static final String DDP_PARTICIPANT_ID = "ddpParticipantId";
    protected static final String PARTICIPANT_ID     = "participantId";

    protected Patch originalPatch;
    protected String ddpParticipantId;

    public static PreProcessor<Patch> produce(PatchPreProcessorPayload patchPreProcessorPayload) {
        PreProcessor<Patch> patchPreProcessor;
        if (isNotParticipantRecordPatchAndIsParentGuid(patchPreProcessorPayload)) {
            patchPreProcessor = new ParentRelatedPatchPreProcessor(new ParticipantDaoImpl());
        } else {
            patchPreProcessor = patch -> patch;
        }
        return patchPreProcessor;
    }

    private static boolean isNotParticipantRecordPatchAndIsParentGuid(PatchPreProcessorPayload patchPreProcessorPayload) {
        //  if the tableAlias is `r` then its' parent is actually planned to be `ddpParticipantId`
        //  which means that the Patch won't need any preprocessing / updating, it will return back the original patch (line 21)
        return !DBConstants.DDP_PARTICIPANT_RECORD_ALIAS.equals(patchPreProcessorPayload.getTableAlias())
                && DDP_PARTICIPANT_ID.equals(patchPreProcessorPayload.getParent());
    }

    protected final void setData(Patch patch) {
        this.originalPatch    = patch;
        this.ddpParticipantId = patch.getDdpParticipantId();
    }

    @Override
    public Patch process(Patch patch) {
        setData(patch);
        return updatePatchIfRequired();
    }

    protected abstract Patch updatePatchIfRequired();
}
