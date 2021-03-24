package org.broadinstitute.dsm.util.triggerListener;

import org.broadinstitute.ddp.util.BasicTriggerListener;


public class LabelCreationTriggerListener extends BasicTriggerListener {


    @Override
    public String getName() {
        return "LABEL_CREATION_LISTENER";
    }

    protected void monitorJobExecution(boolean veto) {

    }
}
