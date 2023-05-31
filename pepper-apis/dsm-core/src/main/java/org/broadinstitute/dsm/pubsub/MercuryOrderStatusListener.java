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
import org.broadinstitute.dsm.db.dao.mercury.MercuryOrderDao;
import org.broadinstitute.dsm.model.mercury.BaseMercuryStatusMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MercuryOrderStatusListener {
    private static final Logger logger = LoggerFactory.getLogger(DSMtasksSubscription.class);

    public static void subscribeToOrderStatus(String projectId, String subscriptionId) {
        // Instantiate an asynchronous message receiver.
        MessageReceiver receiver = (PubsubMessage message, AckReplyConsumer consumer) -> {
            // Handle incoming message, then ack the received message.
            logger.info("Got STATUS message with Id: " + message.getMessageId());

            try {
                processOrderStatus(message);
                logger.info("Processing the message finished");
                consumer.ack();
            } catch (Exception ex) {
                logger.info("about to nack the message", ex);
                consumer.nack();
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
            logger.info("Started pubsub subscription receiver for mercury order status subscription");
        } catch (TimeoutException e) {
            throw new RuntimeException("Timed out while starting pubsub subscription for mercury order status", e);
        }
    }

    private static void processOrderStatus(PubsubMessage message) throws Exception {
        String data = message.getData().toStringUtf8();
        BaseMercuryStatusMessage baseMercuryStatusMessage = new Gson().fromJson(data, BaseMercuryStatusMessage.class);
        MercuryOrderDao.updateOrderStatus(baseMercuryStatusMessage, data);

    }
}
