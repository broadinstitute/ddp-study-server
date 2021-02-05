package org.broadinstitute.ddp.event.dsmtask.api;

import static java.lang.String.format;
import static org.broadinstitute.ddp.event.dsmtask.api.DsmTaskLogUtil.errorMsg;
import static org.broadinstitute.ddp.event.dsmtask.api.DsmTaskLogUtil.infoMsg;
import static org.broadinstitute.ddp.event.dsmtask.api.DsmTaskResultData.DsmTaskResultType.ERROR;
import static org.broadinstitute.ddp.event.dsmtask.api.DsmTaskResultData.DsmTaskResultType.SUCCESS;
import static org.slf4j.LoggerFactory.getLogger;


import org.slf4j.Logger;

public abstract class DsmTaskProcessorAbstract implements DsmTaskProcessor {

    protected static final Logger LOG = getLogger(DsmTaskProcessorAbstract.class);

    @Override
    public DsmTaskResultData processDsmTask(DsmTaskData dsmTaskData) {
        try {
            LOG.info(infoMsg("Task processing STARTED: taskType={}, participantId={}, userId={}, data={}"),
                    dsmTaskData.getTaskType(), dsmTaskData.getParticipantGuid(), dsmTaskData.getUserId(), dsmTaskData.getPayload());

            handleTask(dsmTaskData);

        } catch (DsmTaskException e) {
            LOG.warn(errorMsg(format("Task processing FAILED: dsmTask=%s, participantGuid=%s",
                    dsmTaskData.getTaskType(), dsmTaskData.getParticipantGuid())));
            return new DsmTaskResultData(ERROR, e.getMessage(), dsmTaskData, e.isNeedsToRetry());
        } catch (Exception e) {
            LOG.error(errorMsg(format("Task processing FAILED: dsmTask=%s, participantGuid=%s",
                    dsmTaskData.getTaskType(), dsmTaskData.getParticipantGuid())), e);
            return new DsmTaskResultData(ERROR, e.getMessage(), dsmTaskData, false);
        }

        var dsmTaskResultData = new DsmTaskResultData(SUCCESS, dsmTaskData);

        LOG.info(infoMsg("Task processing COMPLETED: taskType={}, dsmTaskResultData={}"),
                dsmTaskData.getTaskType(), dsmTaskResultData);

        return dsmTaskResultData;
    }

    protected abstract void handleTask(DsmTaskData dsmTaskData);
}
