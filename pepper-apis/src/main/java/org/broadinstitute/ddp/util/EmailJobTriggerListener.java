package org.broadinstitute.ddp.util;


/**
 * Created by ebaker on 5/25/16.
 */
public class EmailJobTriggerListener extends BasicTriggerListener
{

    @Override
    public String getName() {
        return "EMAIL_JOB_LISTENER";
    }

    protected void monitorJobExecution(boolean veto) {

    }
}
