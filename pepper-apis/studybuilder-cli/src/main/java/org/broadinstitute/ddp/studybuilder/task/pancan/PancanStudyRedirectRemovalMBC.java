package org.broadinstitute.ddp.studybuilder.task.pancan;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PancanStudyRedirectRemovalMBC extends PancanStudyRedirectRemovalSupport {

    private static final String searchPexExpr = "[\"ADVANCED_BREAST\"].answers.hasOption(\"YES\")";
    private static final String currentPexExpr = "[\"ADVANCED_BREAST\"].answers.hasOption(\"YES\")";
    private static final String newPexExpr = "[\"ADVANCED_BREAST\"].answers.hasOption(\"N/A\")";

    public PancanStudyRedirectRemovalMBC() {
        super("cmi-mbc", searchPexExpr, currentPexExpr, newPexExpr);
        log.info("TASK:: PancanStudyRedirectRemovalMBC");
    }

}
