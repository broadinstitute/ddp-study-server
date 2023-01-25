package org.broadinstitute.ddp.event.pubsubtask.api;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskException.Severity.WARN;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskLogUtil.infoMsg;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskResult.PubSubTaskResultType.SUCCESS;

import java.util.Properties;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.util.GsonUtil;


/**
 * Abstract implementation of processor which processes PubSubTask.
 * {@link PubSubTaskProcessor} implementations should extend this abstract class.
 */
@Slf4j
public abstract class PubSubTaskProcessorAbstract implements PubSubTaskProcessor {
    protected final Gson gson = GsonUtil.standardGson();

    protected String studyGuid;
    protected String participantGuid;
    protected Properties payloadProps;

    @Override
    public PubSubTaskResult processPubSubTask(PubSubTask pubSubTask) {
        log.info(infoMsg("PubSubTask processing STARTED: {}"), pubSubTask);

        payloadProps = payloadAsProperties(pubSubTask);

        preProcessTask(pubSubTask);

        validateTaskData(pubSubTask);

        handleTask(pubSubTask);

        var pubSubTaskResult = createPubSubTaskResultAfterTaskProcessing(pubSubTask);
        if (pubSubTaskResult.getResultType() == SUCCESS) {
            log.info(infoMsg("PubSubTask processing COMPLETED: taskType={}, pubSubTaskResult={}"),
                    pubSubTask.getTaskType(), pubSubTaskResult);
        } else {
            log.info(infoMsg("PubSubTask processing COMPLETED: taskType={}, pubSubTaskResult={}"),
                    pubSubTask.getTaskType(), pubSubTaskResult);
        }

        return pubSubTaskResult;
    }

    protected abstract void handleTask(PubSubTask pubSubTask);

    protected boolean isEmptyPayloadAllowed() {
        return false;
    }

    protected void validateTaskData(PubSubTask pubSubTask) {
        if (!isEmptyPayloadAllowed() && isBlank(pubSubTask.getPayloadJson())) {
            throw new PubSubTaskException("PubSubTask processing FAILED: empty payload", WARN);
        }
    }

    protected void preProcessTask(PubSubTask pubSubTask) {
        studyGuid = pubSubTask.getAttributes().get(PubSubTask.ATTR_NAME__STUDY_GUID);
        participantGuid = pubSubTask.getAttributes().get(PubSubTask.ATTR_NAME__PARTICIPANT_GUID);
    }


    /**
     * Create an instance of {@link PubSubTaskResult} after a {@link PubSubTask} processing.
     * By default it creates a result of type SUCCESS (because it case of error - an exception is
     * thrown and an ERROR result is created where it is caught).
     * But in some cases it needs to create {@link PubSubTaskResult} with custom content.
     */
    protected PubSubTaskResult createPubSubTaskResultAfterTaskProcessing(PubSubTask pubSubTask) {
        return new PubSubTaskResult(SUCCESS, null, pubSubTask);
    }

    protected Properties payloadAsProperties(PubSubTask pubSubTask) {
        return gson.fromJson(pubSubTask.getPayloadJson(), Properties.class);
    }

    public static void throwIfInvalidPayloadProperty(PubSubTask pubSubTask, String propName, Object propValue) {
        throw new PubSubTaskException(format("PubSubTask '%s' processing FAILED: payload property %s is incorrect: [%s]",
                pubSubTask.getTaskType(), propName, propValue), WARN);
    }

    public static void throwIfInvalidAttribute(PubSubTask pubSubTask, String attrName, String attrValue) {
        throw new PubSubTaskException(format("PubSubTask '%s' processing FAILED: attribute %s is incorrect: [%s]",
                pubSubTask.getTaskType(), attrName, attrValue), WARN);
    }
}
