package org.broadinstitute.dsm.util.triggerListener;

import org.broadinstitute.ddp.util.BasicTriggerListener;


public class GPNotificationTriggerListener extends BasicTriggerListener {


    @Override
    public String getName() {
        return "GP_NOTIFICATION_LISTENER";
    }

    protected void monitorJobExecution(boolean veto) {

    }
}