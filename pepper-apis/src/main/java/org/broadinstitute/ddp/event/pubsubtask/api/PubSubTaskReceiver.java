package org.broadinstitute.ddp.event.pubsubtask.api;

import static java.lang.String.format;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask.ATTR_TASK_TYPE;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskLogUtil.errorMsg;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskLogUtil.infoMsg;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskResult.PubSubTaskResultType.ERROR;
import static org.slf4j.LoggerFactory.getLogger;


import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import org.broadinstitute.ddp.exception.DDPException;
import org.slf4j.Logger;

/**
 * Receive and process PubSubTask-messages fetched from a subscription
 * defined by config param "pubsub.pubSubTasksSubscription" and fetched by subscriber registered by
 * {@link PubSubTaskConnectionService}.
 *
 * <p>When PubSubTask-message is received it is processed by a {@link PubSubTaskProcessor}
 * which selected by a message attribute 'taskType' (from
 * factory {@link PubSubTaskProcessorFactory}.
 *
 * <p>After a task is processed the result of processing is published to the results topic
 * defined by config param "pubsub.pubSubTasksResultTopic".
 * The result message published to the topic by {@link PubSubTaskResultSender}
 */
public class PubSubTaskReceiver implements MessageReceiver {

    private static final Logger LOG = getLogger(PubSubTaskReceiver.class);

    private final ProjectSubscriptionName projectSubscriptionName;
    private final PubSubTaskProcessorFactory pubSubTaskProcessorFactory;
    private final ResultSender pubSubTaskResultSender;

    public PubSubTaskReceiver(ProjectSubscriptionName projectSubscriptionName,
                              PubSubTaskProcessorFactory pubSubTaskProcessorFactory,
                              ResultSender pubSubTaskResultSender) {
        this.projectSubscriptionName = projectSubscriptionName;
        this.pubSubTaskProcessorFactory = pubSubTaskProcessorFactory;
        this.pubSubTaskResultSender = pubSubTaskResultSender;
    }

    @Override
    public void receiveMessage(PubsubMessage message, AckReplyConsumer consumer) {
        PubSubTask pubSubTask = null;
        try {
            pubSubTask = parseMessage(message);
            var pubSubTaskResult = processPubSubTask(pubSubTask);
            consumer.ack();
            sendResponse(pubSubTaskResult);
        } catch (Exception e) {
            handlePubSubTaskProcessingException(consumer, pubSubTask, e);
        }
    }

    private PubSubTaskResult processPubSubTask(PubSubTask pubSubTask) {
        return pubSubTaskProcessorFactory.getPubSubTaskDescriptor(pubSubTask.getTaskType())
                .getPubSubTaskProcessor().processPubSubTask(pubSubTask);
    }

    private PubSubTask parseMessage(PubsubMessage message) {
        String messageId = message.getMessageId();
        String taskType = message.getAttributesOrDefault(ATTR_TASK_TYPE, null);
        String payloadJson = message.getData() != null ? message.getData().toStringUtf8() : null;

        PubSubTask pubSubTask = new PubSubTask(messageId, taskType, message.getAttributesMap(), payloadJson);

        LOG.info(infoMsg("PubSubTask message received[subscription={}, id={}]: {}}"),
                projectSubscriptionName, messageId, pubSubTask);

        var pubSubTaskDescriptor = pubSubTaskProcessorFactory.getPubSubTaskDescriptor(taskType);
        if (pubSubTaskDescriptor == null) {
            throw new PubSubTaskException(format("PubSubTask message [id=%s] has unknown taskType=%s", messageId, taskType),
                    pubSubTask);
        }

        return pubSubTask;
    }

    /**
     * Handle an exception which could happen during:
     * <pre>
     * - PubSubTask message parsing;
     * - PubSubTask processing.
     * </pre>
     * If exception is PubSubException and shouldRetry==true then call nack() - ask service
     * to retry message sending.
     * If exception is DDPException (or it's subclass, like PubSubTaskException) then
     * it should be sent a result ERROR message to result topic (such message should
     * contain errorMessage = e.getMessage()).
     * Other types of exceptions are logged but no any task result sent to result topic.
     */
    private void handlePubSubTaskProcessingException(AckReplyConsumer consumer, PubSubTask pubSubTask, Exception e) {
        if (e instanceof PubSubTaskException && ((PubSubTaskException)e).isShouldRetry()) {
            LOG.warn(errorMsg(format("PubSubTask processing FAILED, will retry: taskType=%s", pubSubTask.getTaskType())));
            consumer.nack();
        } else {
            consumer.ack();
            LOG.error(errorMsg(e.getMessage()), e);
            if (e instanceof DDPException) {
                sendResponse(new PubSubTaskResult(ERROR, e.getMessage(),
                        e instanceof PubSubTaskException && ((PubSubTaskException) e).getPubSubTask() != null
                                ? ((PubSubTaskException) e).getPubSubTask() : pubSubTask));
            }
        }
    }

    private void sendResponse(PubSubTaskResult pubSubTaskResult) {
        if (pubSubTaskResultSender != null) {
            pubSubTaskResultSender.sendPubSubTaskResult(pubSubTaskResult);
        }
    }
}
