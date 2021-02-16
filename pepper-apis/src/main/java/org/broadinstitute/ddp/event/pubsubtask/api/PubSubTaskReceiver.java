package org.broadinstitute.ddp.event.pubsubtask.api;

import static java.lang.String.format;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask.ATTR_TASK_TYPE;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskException.Severity.WARN;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskLogUtil.errorMsg;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskLogUtil.infoMsg;
import static org.slf4j.LoggerFactory.getLogger;


import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
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
            if (pubSubTaskResult.getPubSubTaskResultPayload().getResultType() == PubSubTaskResult.PubSubTaskResultType.ERROR) {
                consumer.nack();
            } else {
                consumer.ack();
                sendResponse(pubSubTaskResult);
            }
        } catch (Exception e) {
            consumer.ack();

            if (e instanceof PubSubTaskException && ((PubSubTaskException)e).getSeverity() == WARN) {
                LOG.warn(errorMsg(e.getMessage()));
            } else {
                LOG.error(errorMsg(e.getMessage()), e);
            }

            if (pubSubTask != null) {
                sendResponse(new PubSubTaskResult(PubSubTaskResult.PubSubTaskResultType.ERROR, e.getMessage(), pubSubTask));
            }
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
            throw new PubSubTaskException(format("PubSubTask message [id=%s] has unknown taskType=%s", messageId, taskType), WARN);
        }

        return pubSubTask;
    }

    private void sendResponse(PubSubTaskResult pubSubTaskResult) {
        if (pubSubTaskResultSender != null) {
            pubSubTaskResultSender.sendPubSubTaskResult(pubSubTaskResult);
        }
    }
}
