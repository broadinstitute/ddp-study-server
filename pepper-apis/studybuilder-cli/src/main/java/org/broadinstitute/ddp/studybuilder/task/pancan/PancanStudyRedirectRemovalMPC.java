package org.broadinstitute.ddp.studybuilder.task.pancan;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PancanStudyRedirectRemovalMPC extends PancanStudyRedirectRemovalSupport {

    private static final String searchPexExpr = "[\"ADVANCED_PROSTATE\"].answers.hasOption(\"YES\")";
    private static final String currentPexExpr = "[\"ADVANCED_PROSTATE\"].answers.hasOption(\"YES\")";
    private static final String newPexExpr = "[\"ADVANCED_PROSTATE\"].answers.hasOption(\"N/A\")";

    public PancanStudyRedirectRemovalMPC() {
        super("cmi-mpc", searchPexExpr, currentPexExpr, newPexExpr);
        log.info("TASK:: PancanStudyRedirectRemovalMPC ");
    }

}
