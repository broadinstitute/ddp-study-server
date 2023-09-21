package org.broadinstitute.dsm.pubsub;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.google.api.gax.core.ExecutorProvider;
import com.google.api.gax.core.InstantiatingExecutorProvider;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.gson.Gson;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.dao.mercury.MercuryOrderDao;
import org.broadinstitute.dsm.exception.DSMPubSubException;
import org.broadinstitute.dsm.model.mercury.BaseMercuryStatusMessage;

@Slf4j
public class MercuryOrderStatusListener {

    private MercuryOrderStatusListener(){
        throw new IllegalStateException("Utility class");
    }
    public static void subscribeToOrderStatus(String projectId, String subscriptionId) {
        // Instantiate an asynchronous message receiver.
        MessageReceiver receiver = (PubsubMessage message, AckReplyConsumer consumer) -> {
            String messageId = message.getMessageId();
            // Handle incoming message, then ack the received message.
            log.info(String.format("Got Mercury status message with Id: {}", messageId));
            try {
                consumer.ack();
                processOrderStatus(message);
                log.info("Processing the status message from Mercury finished");
            } catch (DSMPubSubException error) {
                log.error("Error happened parsing Mercury Status Message, DSM will ack the message", error);
                error.printStackTrace();
            } catch (Exception ex) {
                log.error("Unexpected error: about to nack the status message from Mercury", ex);
                ex.printStackTrace();
            }

        };
        Subscriber subscriber = null;
        ProjectSubscriptionName resultSubName = ProjectSubscriptionName.of(projectId, subscriptionId);
        ExecutorProvider resultsSubExecProvider = InstantiatingExecutorProvider.newBuilder().setExecutorThreadCount(1).build();
        subscriber = Subscriber.newBuilder(resultSubName, receiver).setParallelPullCount(1).setExecutorProvider(resultsSubExecProvider)
                .setMaxAckExtensionPeriod(org.threeten.bp.Duration.ofSeconds(120)).build();
        try {
            subscriber.startAsync().awaitRunning(1L, TimeUnit.MINUTES);
            log.info("Started pubsub subscription receiver for mercury order status subscription");
        } catch (TimeoutException e) {
            throw new DSMPubSubException("Timed out while starting pubsub subscription for mercury order status", e);
        }
    }

    private static void processOrderStatus(PubsubMessage message) {
        String data = message.getData().toStringUtf8();
        BaseMercuryStatusMessage baseMercuryStatusMessage = new Gson().fromJson(data, BaseMercuryStatusMessage.class);
        MercuryOrderDao.updateOrderStatus(baseMercuryStatusMessage, data);
    }
}
