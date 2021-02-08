package org.broadinstitute.ddp.event.pubsubtask.api;

import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskResult.PubSubTaskResultType.ERROR;


import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;

/**
 * Receive and process PubSubTask-messages fetched from a subscription
 * defined by config param "pubsub.pubSubTasksSubscription" and fetched by subscriber registered by
 * {@link PubSubTaskConnectionService}.
 *
 * <p>When PubSubTask-message is received it is processed by a {@link PubSubTaskProcessor}
 * which selected by a message attribute 'taskType' (from
 * factory {@link PubSubTaskProcessorFactory}.
 *
 * <p>After task is processed the result is published to the results topic
 * defined by config param "pubsub.pubSubTasksResultTopic".
 * The result message published to the topic by {@link PubSubTaskResultSender}
 */
public class PubSubTaskReceiver implements MessageReceiver {

    private final PubSubTaskProcessorFactory pubSubTaskProcessorFactory;
    private final PubSubTaskResultSender pubSubTaskResultSender;
    private final PubSubTaskMessageParser pubSubTaskMessageParser;

    public PubSubTaskReceiver(ProjectSubscriptionName projectSubscriptionName,
                              PubSubTaskProcessorFactory pubSubTaskProcessorFactory,
                              PubSubTaskResultSender pubSubTaskResultSender) {
        this.pubSubTaskProcessorFactory = pubSubTaskProcessorFactory;
        this.pubSubTaskResultSender = pubSubTaskResultSender;
        this.pubSubTaskMessageParser = new PubSubTaskMessageParser(projectSubscriptionName, pubSubTaskProcessorFactory);
    }

    @Override
    public void receiveMessage(PubsubMessage message, AckReplyConsumer consumer) {
        var pubSubTaskMessageParserResult = pubSubTaskMessageParser.parseMessage(message, consumer);
        if (pubSubTaskMessageParserResult.getErrorMessage() != null) {
            consumer.ack();
            sendResponse(new PubSubTaskResult(
                    ERROR, pubSubTaskMessageParserResult.getErrorMessage(), pubSubTaskMessageParserResult.getPubSubTaskMessage()));
        } else {
            var pubSubTaskResultMessage = processPubSubTask(pubSubTaskMessageParserResult.getPubSubTaskMessage());
            if (pubSubTaskResultMessage.isNeedsToRetry()) {
                consumer.nack();
            } else {
                consumer.ack();
                sendResponse(pubSubTaskResultMessage.getPubSubTaskResultMessage());
            }
        }
    }

    private PubSubTaskProcessor.PubSubTaskProcessorResult processPubSubTask(PubSubTask pubSubTask) {
        return pubSubTaskProcessorFactory.getPubSubTaskDescriptors(pubSubTask.getTaskType())
                .getPubSubTaskProcessor().processPubSubTask(pubSubTask);
    }

    private void sendResponse(PubSubTaskResult pubSubTaskResult) {
        pubSubTaskResultSender.sendPubSubTaskResult(pubSubTaskResult);
    }
}
