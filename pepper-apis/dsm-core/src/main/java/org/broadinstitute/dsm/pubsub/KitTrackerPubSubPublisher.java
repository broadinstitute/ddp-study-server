package org.broadinstitute.dsm.pubsub;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.api.gax.rpc.ApiException;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;


public class KitTrackerPubSubPublisher {

    private final Logger logger = LoggerFactory.getLogger(KitTrackerPubSubPublisher.class);

    public void publishMessage(String projectId, String topicId, String kits)
            throws IOException, InterruptedException {


        TopicName topicName = TopicName.of(projectId, topicId);
        // Create a publisher instance with default settings bound to the topic
        logger.info("Publishing message to topic: " + topicName);

        ByteString data = ByteString.copyFrom(kits, StandardCharsets.UTF_8);
        PubsubMessage pubsubMessage = PubsubMessage.newBuilder().setData(data).build();

        // Once published, returns a server-assigned message id (unique within the topic)
        Publisher publisher = Publisher.newBuilder(
                ProjectTopicName.of(projectId, topicId)).build();


        try {
            ApiFuture<String> futureKitTracker = publisher.publish(pubsubMessage);
//            String messageId = publisher.publish(pubsubMessage).get();
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
                publisher.shutdown();
                publisher.awaitTermination(1, TimeUnit.MINUTES);
            }
        }

    }

}
