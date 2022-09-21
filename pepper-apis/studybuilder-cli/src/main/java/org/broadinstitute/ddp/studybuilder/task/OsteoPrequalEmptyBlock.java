package org.broadinstitute.ddp.studybuilder.task;

public class OsteoPrequalEmptyBlock extends PrequalEmptyBlock {
    private static final String STUDY_GUID = "CMI-OSTEO";
    private static final String ACTIVITY_CODE  = "PREQUAL";

    public OsteoPrequalEmptyBlock() {
        super(ACTIVITY_CODE, STUDY_GUID);
    }
}
