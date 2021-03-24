package org.broadinstitute.dsm.util.triggerListener;

import org.broadinstitute.ddp.util.BasicTriggerListener;

import java.util.concurrent.atomic.AtomicInteger;

public class NotificationTriggerListener extends BasicTriggerListener {

    @Override
    public String getName() {
        return "NOTIFICATION_LISTENER";
    }

    protected void monitorJobExecution(boolean veto) {

    }
}