package org.broadinstitute.ddp.event.pubsubtask.api;

import static java.lang.String.format;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask.ATTR_NAME__TASK_TYPE;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskException.Severity.WARN;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskLogUtil.errorMsg;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskLogUtil.infoMsg;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskResult.PubSubTaskResultType.ERROR;

import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.common.util.concurrent.AtomicLongMap;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import lombok.extern.slf4j.Slf4j;

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
@Slf4j
public class PubSubTaskReceiver implements MessageReceiver {
    private static final int MAX_NUMBER_OF_RETRIES_FOR_RETRIABLE_ERROR = 5;

    private final AtomicLongMap<String> retryMessageCounters = AtomicLongMap.create();

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
        String taskType = message.getAttributesOrDefault(ATTR_NAME__TASK_TYPE, null);
        String payloadJson = message.getData() != null ? message.getData().toStringUtf8() : null;

        PubSubTask pubSubTask = new PubSubTask(messageId, taskType, message.getAttributesMap(), payloadJson);

        log.info(infoMsg("PubSubTask message received[subscription={}]: {}}"), projectSubscriptionName, pubSubTask);

        var pubSubTaskDescriptor = pubSubTaskProcessorFactory.getPubSubTaskDescriptor(taskType);
        if (pubSubTaskDescriptor == null) {
            throw new PubSubTaskException(format("PubSubTask has unknown taskType=%s", taskType),
                    WARN, pubSubTask);
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
     * If exception is PubSubTaskException having severity WARN then log it with WARN level.
     * Publish info about the error in a response topic.
     */
    private void handlePubSubTaskProcessingException(AckReplyConsumer consumer, PubSubTask pubSubTask, Exception e) {
        if (e instanceof PubSubTaskException && ((PubSubTaskException)e).isShouldRetry()) {
            handleRetriableErrors(consumer, pubSubTask, e);
        } else {
            handleNonRetriableErrors(consumer, pubSubTask, e);
        }
    }

    private void handleRetriableErrors(AckReplyConsumer consumer, PubSubTask pubSubTask, Exception e) {
        long count = retryMessageCounters.incrementAndGet(pubSubTask.getMessageId());
        if (count <= MAX_NUMBER_OF_RETRIES_FOR_RETRIABLE_ERROR) {
            log.warn(errorMsg(format("PubSubTask processing FAILED, will retry (try=%d/%d): taskType=%s, messageId=%s, ErrorMessage: %s",
                    count, MAX_NUMBER_OF_RETRIES_FOR_RETRIABLE_ERROR, pubSubTask.getTaskType(),
                    pubSubTask.getMessageId(), e.getMessage())));
            consumer.nack();
        } else {
            handleNonRetriableErrors(consumer, pubSubTask, e);
        }
    }

    private void handleNonRetriableErrors(AckReplyConsumer consumer, PubSubTask pubSubTask, Exception e) {
        consumer.ack();

        String msg = format(errorMsg("Error processing PubSubTask: taskType=%s, messageId=%s, ErrorMessage: %s"),
                pubSubTask.getTaskType(), pubSubTask.getMessageId(), e.getMessage());
        if (e instanceof PubSubTaskException && ((PubSubTaskException)e).getSeverity() == WARN) {
            log.warn(msg);
        } else {
            log.error(msg, e);
        }

        sendResponse(new PubSubTaskResult(ERROR, e.getMessage(),
                e instanceof PubSubTaskException && ((PubSubTaskException) e).getPubSubTask() != null
                        ? ((PubSubTaskException) e).getPubSubTask() : pubSubTask));
    }

    private void sendResponse(PubSubTaskResult pubSubTaskResult) {
        if (pubSubTaskResultSender != null) {
            pubSubTaskResultSender.sendPubSubTaskResult(pubSubTaskResult);
        }
    }
}
