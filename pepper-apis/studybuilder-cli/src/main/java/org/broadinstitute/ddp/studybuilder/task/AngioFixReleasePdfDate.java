package org.broadinstitute.ddp.studybuilder.task;

import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AngioFixReleasePdfDate extends AbstractFixReleasePdfDate {

    private static final Logger LOG = LoggerFactory.getLogger(AngioFixReleasePdfDate.class);

    public AngioFixReleasePdfDate() {
        super("ANGIO", "ascproject-release", "v1", "ANGIORELEASE");
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
