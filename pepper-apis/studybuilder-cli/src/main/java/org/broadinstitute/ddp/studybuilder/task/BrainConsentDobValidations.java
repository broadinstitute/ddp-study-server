package org.broadinstitute.ddp.studybuilder.task;

public class BrainConsentDobValidations extends InsertActivityValidations {
    public BrainConsentDobValidations() {
        super("cmi-brain", "patches/consent-dob-validations.conf");
    }
}
