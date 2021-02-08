package org.broadinstitute.ddp.event.pubsubtask.api;

import static java.lang.String.format;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskLogUtil.errorMsg;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskLogUtil.infoMsg;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskResult.PubSubTaskResultType.ERROR;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskResult.PubSubTaskResultType.SUCCESS;
import static org.slf4j.LoggerFactory.getLogger;


import org.slf4j.Logger;

/**
 * Abstract implementation of processor which processes PubSubTask.
 * {@link PubSubTaskProcessor} implementations should extend this abstract class.
 */
public abstract class PubSubTaskProcessorAbstract implements PubSubTaskProcessor {

    protected static final Logger LOG = getLogger(PubSubTaskProcessorAbstract.class);

    @Override
    public PubSubTaskProcessorResult processPubSubTask(PubSubTask pubSubTask) {
        boolean needsToRetry = false;
        PubSubTaskResult.PubSubTaskResultType pubSubTaskResultType = SUCCESS;
        String errorMessage = null;
        try {
            LOG.info(infoMsg("Task processing STARTED: taskType={}, participantId={}, userId={}, data={}"),
                    pubSubTask.getTaskType(), pubSubTask.getParticipantGuid(),
                    pubSubTask.getUserId(), pubSubTask.getPayloadJson());

            handleTask(pubSubTask);

        } catch (PubSubTaskException e) {
            LOG.warn(errorMsg(format("Task processing FAILED, will retry: tastType=%s, participantGuid=%s",
                    pubSubTask.getTaskType(), pubSubTask.getParticipantGuid())));
            needsToRetry = e.isNeedsToRetry();
            pubSubTaskResultType = ERROR;
            errorMessage = e.getMessage();
        } catch (Exception e) {
            LOG.error(errorMsg(format("Task processing FAILED: tastType=%s, participantGuid=%s",
                    pubSubTask.getTaskType(), pubSubTask.getParticipantGuid())), e);
            pubSubTaskResultType = ERROR;
            errorMessage = e.getMessage();
        }

        var pubSubTaskProcessorResult = new PubSubTaskProcessorResult(
                new PubSubTaskResult(pubSubTaskResultType, errorMessage, pubSubTask), needsToRetry);

        LOG.info(infoMsg("Task processing COMPLETED: taskType={}, pubSubTaskResultMessage={}"),
                pubSubTask.getTaskType(), pubSubTaskProcessorResult.getPubSubTaskResultMessage());

        return pubSubTaskProcessorResult;
    }

    protected abstract void handleTask(PubSubTask pubSubTask);
}
