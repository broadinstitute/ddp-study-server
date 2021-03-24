package org.broadinstitute.ddp.util;

/**
 * Created by ebaker on 5/25/16.
 */
public class DatStatJobTriggerListener extends BasicTriggerListener
{


    @Override
    public String getName() {
        return "DATSTAT_JOB_LISTENER";
    }

    protected void monitorJobExecution(boolean veto) {

    }
}
