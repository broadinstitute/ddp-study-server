package org.broadinstitute.dsm.util.triggerListener;

import org.broadinstitute.ddp.util.BasicTriggerListener;


public class ExternalShipperTriggerListener extends BasicTriggerListener {


    @Override
    public String getName() {
        return "EXTERNAL_SHIPPER_LISTENER";
    }

    protected void monitorJobExecution(boolean veto) {

    }
}
