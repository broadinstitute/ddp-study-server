package org.broadinstitute.lddp.util;


import org.broadinstitute.lddp.util.BasicTriggerListener;

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
