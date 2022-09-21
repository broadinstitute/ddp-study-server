package org.broadinstitute.ddp.studybuilder.task;

public class LmsPrequalEmptyBlock extends PrequalEmptyBlock {
    private static final String STUDY_GUID = "cmi-lms";
    private static final String ACTIVITY_CODE  = "PREQUAL";

    public LmsPrequalEmptyBlock() {
        super(ACTIVITY_CODE, STUDY_GUID);
    }
}
