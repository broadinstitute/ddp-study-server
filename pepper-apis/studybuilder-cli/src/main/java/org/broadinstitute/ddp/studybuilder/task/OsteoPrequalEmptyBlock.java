package org.broadinstitute.ddp.studybuilder.task;

public class OsteoPrequalEmptyBlock extends PrequalEmptyBlock {
    private static final String STUDY_GUID = "CMI-OSTEO";
    private static final String ACTIVITY_CODE  = "PREQUAL";
    private static final String SHOWN_EXPRESSION = "user.studies[\"CMI-OSTEO\"].forms[\"PREQUAL\"]."
            + "questions[\"PREQUAL_SELF_DESCRIBE\"].answers.hasOption(\"CHILD_DIAGNOSED\") ||"
            + "user.studies[\"CMI-OSTEO\"].forms[\"PREQUAL\"].questions[\"PREQUAL_SELF_DESCRIBE\"].answers.hasOption(\"DIAGNOSED\")";

    public OsteoPrequalEmptyBlock() {
        super(ACTIVITY_CODE, STUDY_GUID, SHOWN_EXPRESSION);
    }
}
