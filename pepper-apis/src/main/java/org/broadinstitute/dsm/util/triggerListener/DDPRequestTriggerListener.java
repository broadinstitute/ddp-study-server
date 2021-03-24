package org.broadinstitute.dsm.util.triggerListener;

import org.broadinstitute.ddp.util.BasicTriggerListener;


public class DDPRequestTriggerListener extends BasicTriggerListener {



    @Override
    public String getName() {
        return "DDP_REQUEST_LISTENER";
    }

    protected void monitorJobExecution(boolean veto) {

    }
}
