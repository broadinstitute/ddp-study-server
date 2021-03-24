package org.broadinstitute.dsm.pubsub;

import com.google.api.gax.batching.FlowControlSettings;
import com.google.api.gax.core.ExecutorProvider;
import com.google.api.gax.core.InstantiatingExecutorProvider;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import org.broadinstitute.dsm.db.EditParticipantMessage;
import org.broadinstitute.dsm.statics.DBConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class PubSubResultMessageSubscription {

    private static final Logger logger = LoggerFactory.getLogger(PubSubResultMessageSubscription.class);

    public static void dssToDsmSubscriber(String projectId, String subscriptionId) throws Exception {
        subscribeWithFlowControlSettings(projectId, subscriptionId);
    }

    public static void subscribeWithFlowControlSettings (
            String projectId, String subscriptionId) {

        // Instantiate an asynchronous message receiver.
        MessageReceiver receiver =
                (PubsubMessage pubsubMessage, AckReplyConsumer consumer) -> {
                    // Handle incoming message, then ack the received message.
                    logger.info("Id: " + pubsubMessage.getMessageId());
                    logger.info("Data: " + pubsubMessage.getData().toStringUtf8());
                    String message = transformMessage(pubsubMessage);
                    JsonObject jsonObject = new Gson().fromJson(message, JsonObject.class);
                    String userId = null;

                    consumer.ack();

                    if (jsonObject.has("userId")) {
                        userId = jsonObject.get("userId").getAsString();
                        EditParticipantMessage.updateMessage(Integer.parseInt(userId), DBConstants.MESSAGE_RECEIVED_STATUS,
                                message, System.currentTimeMillis());
                    }
                };

        //Subscriber subscriber = null;

        // The subscriber will pause the message stream and stop receiving more messsages from the
        // server if any one of the conditions is met.
        FlowControlSettings flowControlSettings =
                FlowControlSettings.newBuilder()
                        // 1,000 outstanding messages. Must be >0. It controls the maximum number of messages
                        // the subscriber receives before pausing the message stream.
                        .setMaxOutstandingElementCount(1000L)
                        // 100 MiB. Must be >0. It controls the maximum size of messages the subscriber
                        // receives before pausing the message stream.
                        .setMaxOutstandingRequestBytes(100L * 1024L * 1024L)
                        .build();

        Subscriber subscriber = null;
        ProjectSubscriptionName resultSubName = ProjectSubscriptionName.of(projectId, subscriptionId);
        ExecutorProvider resultsSubExecProvider = InstantiatingExecutorProvider.newBuilder().setExecutorThreadCount(1).build();
        subscriber = Subscriber.newBuilder(resultSubName, receiver)
                .setParallelPullCount(1)
                .setExecutorProvider(resultsSubExecProvider)
                .setMaxAckExtensionPeriod(org.threeten.bp.Duration.ofSeconds(120))
                .setFlowControlSettings(flowControlSettings)
                .build();
        try {
            subscriber.startAsync().awaitRunning(1L, TimeUnit.MINUTES);
            logger.info("Started pubsub subscription receiver for {}", subscriptionId);
        }
        catch (TimeoutException e) {
            throw new RuntimeException("Timed out while starting pubsub subscription " + subscriptionId, e);
        }

    }

    public static String transformMessage(PubsubMessage pubsubMessage) {
        String message = pubsubMessage.getData().toStringUtf8();
        JsonObject myMessage = new Gson().fromJson(message, JsonObject.class);
        Map<String, String> attributesMap = pubsubMessage.getAttributesMap();
        attributesMap.forEach(myMessage::addProperty);
        message = myMessage.toString();
        return message;
    }
}
