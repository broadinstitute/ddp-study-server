package org.broadinstitute.ddp.studybuilder.task.pancan;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PancanStudyRedirectRemovalBrain extends PancanStudyRedirectRemovalSupport {

    private static final String searchPexExpr = "hasOptionStartsWith(\"C_BRAIN_\")";
    private static final String currentPexExpr = "hasOptionStartsWith(\"C_BRAIN_\")";
    private static final String newPexExpr = "hasOptionStartsWith(\"C_BRAIN_ENROLL_ENDED\")";

    public PancanStudyRedirectRemovalBrain() {
        super("cmi-brain", searchPexExpr, currentPexExpr, newPexExpr);
        log.info("TASK:: PancanStudyRedirectRemovalBrain ");
    }

}
