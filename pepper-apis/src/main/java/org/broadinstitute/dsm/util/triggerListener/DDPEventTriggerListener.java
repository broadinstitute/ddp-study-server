package org.broadinstitute.dsm.util.triggerListener;

import org.broadinstitute.ddp.util.BasicTriggerListener;


public class DDPEventTriggerListener extends BasicTriggerListener {

    @Override
    public String getName() {
        return "DDP_EVENT_LISTENER";
    }

    protected void monitorJobExecution(boolean veto) {

    }
}