package org.broadinstitute.dsm.util.triggerListener;

import org.broadinstitute.ddp.util.BasicTriggerListener;


public class UPSTriggerListener extends BasicTriggerListener {

    @Override
    public String getName() {
        return "UPS_LISTENER";
    }

    protected void monitorJobExecution(boolean veto) {

    }
}


