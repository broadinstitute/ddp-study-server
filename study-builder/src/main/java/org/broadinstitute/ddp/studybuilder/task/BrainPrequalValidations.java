package org.broadinstitute.ddp.studybuilder.task;

public class BrainPrequalValidations extends InsertActivityValidations {
    public BrainPrequalValidations() {
        super("cmi-brain", "patches/prequal-validations.conf");
    }
}
