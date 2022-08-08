package org.broadinstitute.dsm.pubsub;

import java.io.IOException;
import java.util.Map;
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
import org.broadinstitute.dsm.db.EditParticipantMessage;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.DSMConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EditParticipantMessagePublisher {
    private static final Logger logger = LoggerFactory.getLogger(EditParticipantMessagePublisher.class);

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
    
    public static void publishMessage(String data, Map<String, String> attributeMap, String projectId, String topicId) throws Exception {
        logger.info("Publishing message to dss");
        publishWithErrorHandler(projectId, topicId, data, attributeMap);
    }

    public static void publishWithErrorHandler(String projectId, String topicId, String messageData, Map<String, String> attributeMap)
            throws IOException, InterruptedException {
        TopicName topicName = TopicName.of(projectId, topicId);
        Publisher publisher = null;

        try {
            // Create a publisher instance with default settings bound to the topic
            publisher = createPublisher(topicName);

            ByteString data = ByteString.copyFromUtf8(messageData);
            PubsubMessage pubsubMessage = PubsubMessage.newBuilder()
                    .setData(data)
                    .putAllAttributes(attributeMap)
                    .build();

            // Once published, returns a server-assigned message id (unique within the topic)
            ApiFuture<String> future = publisher.publish(pubsubMessage);
            EditParticipantMessage.insertMessage(new EditParticipantMessage(Integer.parseInt(attributeMap.get("userId")),
                    DBConstants.MESSAGE_PUBLISHING_STATUS, System.currentTimeMillis()));

            // Add an asynchronous callback to handle success / failure
            ApiFutures.addCallback(
                    future,
                    new ApiFutureCallback<String>() {

                        @Override
                        public void onFailure(Throwable throwable) {
                            if (throwable instanceof ApiException) {
                                ApiException apiException = ((ApiException) throwable);
                                // details on the API exception
                                logger.info(String.valueOf(apiException.getStatusCode().getCode()));
                                logger.info(String.valueOf(apiException.isRetryable()));
                            }
                            logger.error("Error publishing message " + topicId);
                        }

                        @Override
                        public void onSuccess(String messageId) {
                            // Once published, returns server-assigned message ids (unique within the topic)
                            logger.info("Published message ID: " + messageId);
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
