package org.broadinstitute.ddp.studybuilder.task;

import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BrainFixReleasePdfDate extends AbstractFixReleasePdfDate {

    private static final Logger LOG = LoggerFactory.getLogger(BrainFixReleasePdfDate.class);

    public BrainFixReleasePdfDate() {
        super("cmi-brain", "brainproject-release", "v1", "RELEASE");
    }

    @Override
    public void run(Handle handle) {
        boolean updated = fixActivityDateSubstitution(handle);
        if (updated) {
            LOG.info("Updated release pdf ACTIVITY_DATE substitution to reference release activity");
        } else {
            LOG.info("Release pdf ACTIVITY_DATE substitution is already referencing release activity");
        }
    }
}
