package org.broadinstitute.ddp.event.dsmtask.api;

import static org.broadinstitute.ddp.event.dsmtask.api.DsmTaskResultData.DsmTaskResultType.ERROR;
import static org.broadinstitute.ddp.event.dsmtask.api.DsmTaskResultData.DsmTaskResultType.SUCCESS;

public abstract class DsmTaskProcessorAbstract implements DsmTaskProcessor {

    @Override
    public DsmTaskResultData processDsmTask(DsmTaskData dsmTaskData) {
        try {
            doIt(dsmTaskData);
        } catch (Exception e) {
            return new DsmTaskResultData(ERROR, e.getMessage(), dsmTaskData);
        }
        return new DsmTaskResultData(SUCCESS, dsmTaskData);
    }

    protected abstract void doIt(DsmTaskData dsmTaskData);
}
