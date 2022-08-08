package org.broadinstitute.dsm.pubsub;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;

import io.grpc.ManagedChannelBuilder;

import org.broadinstitute.dsm.DSMServer;
import org.broadinstitute.dsm.util.DSMConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class KitTrackerPubSubPublisher {

    private final Logger logger = LoggerFactory.getLogger(KitTrackerPubSubPublisher.class);

    public static Publisher createPublisher(TopicName topicName) throws IOException {
        var emulatorEnabled = Boolean.parseBoolean(DSMConfig.getStringIfPresent(DSMServer.GCP_PATH_TO_USE_PUBSUB_EMULATOR));
        
        if (emulatorEnabled) {
            var host = DSMConfig.getSqlFromConfig(DSMServer.GCP_PATH_TO_PUBSUB_HOST);
            var credentialsProvider = NoCredentialsProvider.create();
            var channel = ManagedChannelBuilder
                .forTarget(host)
                .usePlaintext()
                .build();
            var channelProvider = FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel));

            return Publisher.newBuilder(topicName)
                    .setCredentialsProvider(credentialsProvider)
                    .setChannelProvider(channelProvider)
                    .build();
        } else {
            return Publisher.newBuilder(topicName).build();
        }
    }

    public void publishMessage(String projectId, String topicId, String kits)
            throws IOException, InterruptedException {


        TopicName topicName = TopicName.of(projectId, topicId);
        // Create a publisher instance with default settings bound to the topic
        logger.info("Publishing message to topic: " + topicName);

        ByteString data = ByteString.copyFrom(kits, StandardCharsets.UTF_8);
        PubsubMessage pubsubMessage = PubsubMessage.newBuilder().setData(data).build();

        // Once published, returns a server-assigned message id (unique within the topic)
        Publisher publisher = createPublisher(topicName);

        try {
            ApiFuture<String> futureKitTracker = publisher.publish(pubsubMessage);
            // String messageId = publisher.publish(pubsubMessage).get();
            ApiFutures.addCallback(
                    futureKitTracker,
                    new ApiFutureCallback<String>() {

                        @Override
                        public void onFailure(Throwable throwable) {
                            if (throwable instanceof ApiException) {
                                ApiException apiException = ((ApiException) throwable);
                                // details on the API exception
                                logger.info(String.valueOf(apiException.getStatusCode().getCode()));
                                logger.info(String.valueOf(apiException.isRetryable()));
                            }
                            logger.info("Error publishing Pub/Sub message: ");
                        }

                        @Override
                        public void onSuccess(String messageId) {
                            // Once published, returns server-assigned message ids (unique within the topic)
                            logger.info("Pubsub message published. MessageId: " + messageId);
                            logger.info("Pubsub Message : " + kits);
                        }
                    },
                    MoreExecutors.directExecutor()
            );
        } finally {
            if (publisher != null) {
                // When finished with the publisher, shutdown to free up resources.
                try {
                    publisher.shutdown();
                    publisher.awaitTermination(1, TimeUnit.MINUTES);
                } catch (Exception e) {
                    //TODO DSM
                }
            }
        }

    }

}
