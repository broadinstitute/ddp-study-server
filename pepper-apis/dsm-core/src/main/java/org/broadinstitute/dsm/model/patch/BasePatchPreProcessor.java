package org.broadinstitute.dsm.model.patch;

// A base class for processing and transforming Patch instance.
// A child class must implement updatePatchIfRequired();
public abstract class BasePatchPreProcessor implements PreProcessor<Patch> {

    protected Patch originalPatch;
    protected String ddpParticipantId;

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
