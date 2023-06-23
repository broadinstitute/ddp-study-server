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
import lombok.AllArgsConstructor;
import lombok.Value;
import org.broadinstitute.dsm.db.SomaticResultUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AntivirusScanningStatusListener {
    private static final Logger logger = LoggerFactory.getLogger(AntivirusScanningStatusListener.class);

    public static void subscribeToAntiVirusStatus(String projectId, String subscriptionId) {
        // Instantiate an asynchronous message receiver.
        MessageReceiver receiver = (PubsubMessage message, AckReplyConsumer consumer) -> {
            // Handle incoming message, then ack the received message.
            logger.info("Got STATUS message with Id: {}", message.getMessageId());

            try {
                processAntiVirusStatus(message);
                logger.info("Processing the message finished");
                consumer.ack();
            } catch (Exception ex) {
                logger.info("about to nack the message {}", ex.getMessage());
                logger.error("Error updating antivirus status from pub/sub: {}", ex.getMessage());
                consumer.nack();
                ex.printStackTrace();
            }

        };
        ProjectSubscriptionName resultSubName = ProjectSubscriptionName.of(projectId, subscriptionId);
        ExecutorProvider resultsSubExecProvider = InstantiatingExecutorProvider.newBuilder().setExecutorThreadCount(1).build();
        Subscriber subscriber = Subscriber.newBuilder(resultSubName, receiver).setParallelPullCount(1)
                .setExecutorProvider(resultsSubExecProvider).setMaxAckExtensionPeriod(org.threeten.bp.Duration.ofSeconds(120)).build();
        try {
            subscriber.startAsync().awaitRunning(1L, TimeUnit.MINUTES);
            logger.info("Started pubsub subscription receiver for antivirus scanning status.");
        } catch (TimeoutException e) {
            throw new RuntimeException("Timed out while starting pubsub subscription for antivirus scanning status", e);
        }
    }

    private static void processAntiVirusStatus(PubsubMessage message) {
        String data = message.getData().toStringUtf8();
        AntivirusMessage antivirusMessage = new Gson().fromJson(data, AntivirusMessage.class);
        if (antivirusMessage.bucketId != null && antivirusMessage.objectId != null && antivirusMessage.scanResult != null) {
            switch (antivirusMessage.scanResult) {
                case CLEAN:
                    SomaticResultUpload.updateSuccessfulVirusScanningResult(antivirusMessage.bucketId,
                            antivirusMessage.objectId, antivirusMessage.newBucketId, antivirusMessage.newObjectId);
                    break;
                case DELETED:
                    SomaticResultUpload.updateUnsuccessfulVirusScanningResult(antivirusMessage.bucketId, antivirusMessage.objectId);
                    break;
                default:
                    break;
            }
        }
    }

    private enum ScanResult {
        CLEAN,
        DELETED
    }

    @Value
    @AllArgsConstructor
    public static class AntivirusMessage {
        String bucketId;
        String objectId;
        String newBucketId;
        String newObjectId;
        ScanResult scanResult;

    }
}
