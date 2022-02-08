package org.broadinstitute.ddp.studybuilder.task;

public class BrainAddUsTerritoriesToRules extends AbstractAddUsTerritoriesToRules {

    @Override
    public String getDataFileName() {
        return "patches/territory-pex-expression-updates.conf";
    }

    @Override
    public String getStudyGuid() {
        return "cmi-brain";
    }

}
