package org.broadinstitute.ddp.event.pubsubtask.api;

import static java.lang.String.format;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskLogUtil.errorMsg;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskLogUtil.infoMsg;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskResult.PubSubTaskResultType.ERROR;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskResult.PubSubTaskResultType.SUCCESS;
import static org.slf4j.LoggerFactory.getLogger;


import com.google.gson.Gson;
import org.broadinstitute.ddp.util.GsonUtil;
import org.slf4j.Logger;


/**
 * Abstract implementation of processor which processes PubSubTask.
 * {@link PubSubTaskProcessor} implementations should extend this abstract class.
 */
public abstract class PubSubTaskProcessorAbstract implements PubSubTaskProcessor {

    protected static final Logger LOG = getLogger(PubSubTaskProcessorAbstract.class);

    protected final Gson gson = GsonUtil.standardGson();

    @Override
    public PubSubTaskResult processPubSubTask(PubSubTask pubSubTask) {
        PubSubTaskResult.PubSubTaskResultType pubSubTaskResultType = SUCCESS;
        String errorMessage = null;
        try {
            LOG.info(infoMsg("PubSubTask processing STARTED: {}"), pubSubTask);

            handleTask(pubSubTask);

        } catch (PubSubTaskException e) {
            if (!e.isShouldRetry()) {
                throw e;
            } else {
                LOG.warn(errorMsg(format("PubSubTask processing FAILED, will retry: taskType=%s", pubSubTask.getTaskType())));
            }
            pubSubTaskResultType = ERROR;
            errorMessage = e.getMessage();
        }

        var pubSubTaskResult = new PubSubTaskResult(pubSubTaskResultType, errorMessage, pubSubTask);

        LOG.info(infoMsg("PubSubTask processing COMPLETED: taskType={}, pubSubTaskResult={}"),
                pubSubTask.getTaskType(), pubSubTaskResult);

        setResultCustomData(pubSubTask, pubSubTaskResult);

        return pubSubTaskResult;
    }

    protected abstract void handleTask(PubSubTask pubSubTask);

    protected void setResultCustomData(PubSubTask pubSubTask, PubSubTaskResult pubSubTaskResult) {
    }
}
