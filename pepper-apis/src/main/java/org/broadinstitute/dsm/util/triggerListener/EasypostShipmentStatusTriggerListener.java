package org.broadinstitute.dsm.util.triggerListener;

import org.broadinstitute.ddp.util.BasicTriggerListener;


public class EasypostShipmentStatusTriggerListener extends BasicTriggerListener {


    @Override
    public String getName() {
        return "EASYPOST_SHIPMENT_STATUS";
    }

    protected void monitorJobExecution(boolean veto) {

    }
}
