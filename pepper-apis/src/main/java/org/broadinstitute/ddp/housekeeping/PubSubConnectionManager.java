package org.broadinstitute.ddp.housekeeping;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.api.gax.rpc.StatusCode;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.SubscriptionAdminSettings;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.cloud.pubsub.v1.TopicAdminSettings;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PushConfig;
import com.google.pubsub.v1.Subscription;
import com.google.pubsub.v1.Topic;
import com.google.pubsub.v1.TopicName;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.broadinstitute.ddp.util.PubSubEmulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generally useful utilities for connecting to pubsub
 */
public class PubSubConnectionManager {

    private static final Logger LOG = LoggerFactory.getLogger(PubSubConnectionManager.class);

    public static final int ACK_DEADLINE_SECONDS = 60;
    public static final long SUB_EXPIRATION_DAYS = 10L;

    private final boolean useEmulator;

    private ManagedChannel pubsubChannel;

    private CredentialsProvider pubsubCredsProvider;

    private TransportChannelProvider channelProvider;

    private final TopicAdminClient adminClient;

    private final SubscriptionAdminClient subscriptionAdminClient;

    private static final Collection<Publisher> PUBLISHERS_TO_SHUTDOWN = new ArrayList<>();

    static {
        // last gasp attempt to shutdown any created publishers
        // that have not been cleaned up properly
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (Publisher publisher : PUBLISHERS_TO_SHUTDOWN) {
                try {
                    publisher.shutdown();
                } catch (Exception e) {
                    LOG.error("Could not shutdown publisher for topic {}", publisher.getTopicName(), e);
                }
            }
        }));
    }

    /**
     * Creates a new one
     *
     * @param useEmulator if true, boots pubsub emulator locally if needed.
     *                    Otherwise, assumes this is running in an environment
     *                    where GCP pubsub resources are available
     */
    public PubSubConnectionManager(boolean useEmulator) {
        this.useEmulator = useEmulator;
        if (useEmulator) {
            if (!PubSubEmulator.hasStarted()) {
                LOG.info("Starting simulator");
                PubSubEmulator.startEmulator();
            }
            pubsubChannel = emulatedPubSubChannel();
            pubsubCredsProvider = emulatedPubSubCredentialsProvider();
            channelProvider = FixedTransportChannelProvider.create(GrpcTransportChannel.create(pubsubChannel));
        }
        try {
            adminClient = createClient();
            subscriptionAdminClient = createSubscriptionClient();
        } catch (IOException e) {
            throw new RuntimeException("Could not create admin client", e);
        }
    }

    /**
     * Creates a new client
     */
    private SubscriptionAdminClient createSubscriptionClient() throws IOException {
        if (useEmulator) {
            SubscriptionAdminSettings adminSettings = SubscriptionAdminSettings.newBuilder()
                    .setCredentialsProvider(pubsubCredsProvider)
                    .setTransportChannelProvider(channelProvider)
                    .build();
            return SubscriptionAdminClient.create(adminSettings);
        } else {
            return SubscriptionAdminClient.create(); // todo arz shutdown listener
        }
    }

    /**
     * Returns a subscription for the given project and subscription.  If one exists,
     * it is re-used.  Otherwise, a new one is created.
     */
    public Subscription createSubscriptionIfNotExists(ProjectSubscriptionName projectSubscriptionName,
                                                      ProjectTopicName projectTopicName) {
        return createSubscriptionIfNotExists(Subscription.newBuilder()
                .setName(projectSubscriptionName.toString())
                .setTopic(projectTopicName.toString())
                .setPushConfig(PushConfig.getDefaultInstance())
                .setAckDeadlineSeconds(ACK_DEADLINE_SECONDS)
                .build());
    }

    public Subscription createSubscriptionIfNotExists(Subscription subscription) {
        try {
            subscriptionAdminClient.createSubscription(subscription);
        } catch (ApiException e) {
            if (e.getStatusCode().getCode() != StatusCode.Code.ALREADY_EXISTS) {
                throw new RuntimeException(String.format("Error creating subscription %s to topic %s",
                        subscription.getName(),
                        subscription.getTopic()), e);
            } else {
                LOG.info("Subscription {} for topic {} already exists", subscription.getName(), subscription.getTopic());
            }
        }
        return subscription;
    }

    /**
     * Creates the topic if it doesn't already exist.
     */
    public Topic createTopicIfNotExists(ProjectTopicName topicName) {
        Topic topic = null;
        try {
            topic = adminClient.createTopic(topicName);
            LOG.info("Created topic {} for project {}", topicName.getTopic(), topicName.getProject());
        } catch (ApiException e) {
            if (e.getStatusCode().getCode() != StatusCode.Code.ALREADY_EXISTS) {
                throw new RuntimeException(String.format("Error creating topic %s in project %s.  Status code: %s.  "
                                                                 + "IsRetryable: %s",
                                                         topicName.getTopic(),
                                                         topicName.getProject(),
                                                         e.getStatusCode().getCode(),
                                                         e.isRetryable()));
            } else {
                LOG.info("Topic {} already exists in project {}", topicName.getTopic(), topicName.getProject());
            }
        } catch (Exception e) {
            throw new RuntimeException(String.format("Error creating topic %s in project %s",
                                                     topicName.getTopic(),
                                                     topicName.getProject()), e);
        }
        return topic;
    }

    /**
     * Creates a new client.  Callers should be careful
     * to call {@link TopicAdminClient#close} when done
     * with the client to avoid resource leakage
     */
    private TopicAdminClient createClient() throws IOException {
        if (useEmulator) {
            return TopicAdminClient.create(TopicAdminSettings.newBuilder()
                            .setTransportChannelProvider(channelProvider)
                            .setCredentialsProvider(pubsubCredsProvider)
                            .build());
        } else {
            return TopicAdminClient.create();  // todo arz shutdown listener
        }
    }

    /**
     * Creates a new publisher.  Callers should be careful to
     * call {@link Publisher#shutdown} when done with the publisher
     * to avoid resource leaks
     */
    public Publisher createPublisher(TopicName topicName) throws IOException {
        Publisher publisher = null;
        if (useEmulator) {
            publisher = Publisher.newBuilder(topicName)
                    .setChannelProvider(channelProvider)
                    .setCredentialsProvider(pubsubCredsProvider)
                    .build();
        } else {
            publisher = Publisher.newBuilder(topicName).build();
        }
        // add the publisher to the list of things to shutdown for better cleanup
        PUBLISHERS_TO_SHUTDOWN.add(publisher);
        return publisher;
    }

    /**
     * Creates the creds provider needed for the emulator.
     * @return
     */
    private CredentialsProvider emulatedPubSubCredentialsProvider() {
        if (!useEmulator) {
            throw new RuntimeException("You shouldn't be calling this when using the real "
                                               + "(non-emulator) implementation");
        }
        return NoCredentialsProvider.create();
    }

    /**
     * Creates the channel need for the emulator
     * @return
     */
    private ManagedChannel emulatedPubSubChannel() {
        if (!useEmulator) {
            throw new RuntimeException("You shouldn't be calling this when using the real "
                                               + "(non-emulator) implementation");
        }
        String hostport = System.getenv("PUBSUB_EMULATOR_HOST");
        return ManagedChannelBuilder.forTarget(hostport).usePlaintext(true).build();
    }

    /**
     * Closes, releases, shuts down, and otherwise
     * cleans up things that might leak resources.
     */
    public void close() {
        if (subscriptionAdminClient != null) {
            try {
                subscriptionAdminClient.close();
            } catch (Exception e) {
                LOG.error("Could not clean up subscription admin client", e);
            }
        }
        if (adminClient != null) {
            try {
                adminClient.close();
            } catch (Exception e) {
                LOG.error("Could not clean up admin client", e);
            }
        }
        if (pubsubChannel != null) {
            pubsubChannel.shutdown();
        }
    }

    /**
     * Creates a new subscriber
     */
    public Subscriber subscribe(ProjectSubscriptionName projectSubscriptionName, MessageReceiver receiver) {
        if (useEmulator) {
            return Subscriber.newBuilder(projectSubscriptionName, receiver)
                    .setCredentialsProvider(pubsubCredsProvider)
                    .setChannelProvider(channelProvider)
                    .build();
        } else {
            return Subscriber.newBuilder(projectSubscriptionName, receiver).build();
        }
    }

}
