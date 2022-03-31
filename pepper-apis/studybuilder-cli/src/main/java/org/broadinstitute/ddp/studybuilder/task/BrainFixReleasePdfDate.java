package org.broadinstitute.ddp.studybuilder.task;

import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Handle;

@Slf4j
public class BrainFixReleasePdfDate extends AbstractFixReleasePdfDate {
    public BrainFixReleasePdfDate() {
        super("cmi-brain", "brainproject-release", "v1", "RELEASE");
    }

    @Override
    public void run(Handle handle) {
        boolean updated = fixActivityDateSubstitution(handle);
        if (updated) {
            log.info("Updated release pdf ACTIVITY_DATE substitution to reference release activity");
        } else {
            log.info("Release pdf ACTIVITY_DATE substitution is already referencing release activity");
        }
    }
}
