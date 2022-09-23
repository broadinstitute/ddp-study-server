package org.broadinstitute.ddp.studybuilder.task;

public class BrainPrequalEmptyBlock extends PrequalEmptyBlock {
    private static final String STUDY_GUID = "cmi-brain";
    private static final String ACTIVITY_CODE  = "PREQUAL";

    public BrainPrequalEmptyBlock() {
        super(ACTIVITY_CODE, STUDY_GUID);
    }
}
