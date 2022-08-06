package org.broadinstitute.ddp.housekeeping;

import java.io.IOException;
import java.lang.ref.Cleaner;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.AlreadyExistsException;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.api.gax.rpc.NotFoundException;
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

import io.grpc.ManagedChannelBuilder;
import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * Generally useful utilities for connecting to pubsub
 */
@Slf4j
public class PubSubConnectionManager {
    private static final Cleaner cleaner = Cleaner.create();
    public static final int ACK_DEADLINE_SECONDS = 60;

    @Value
    static class State implements Runnable {
        private TopicAdminClient topicAdminClient;
        private SubscriptionAdminClient subscriptionAdminClient;
        private Map<String, Publisher> publishers = new HashMap<>();

        State(TopicAdminClient topicAdminClient, SubscriptionAdminClient subscriptionAdminClient) {
            this.topicAdminClient = topicAdminClient;
            this.subscriptionAdminClient = subscriptionAdminClient;
        }

        public void run() {
            topicAdminClient.close();
            subscriptionAdminClient.close();

            for (var entrySet : publishers.entrySet()) {
                var publisher = entrySet.getValue();

                try {
                    publisher.shutdown();
                } catch (IllegalStateException cause) {
                    // Publisher's already shutdown, nothing to do
                } catch (Exception cause) {
                    // Some other error occurred during shutdown.
                    // Too late to do anything about it, so just continue on
                    // (bskinner, 20220805)
                    //   Consider logging, but check Lombok's implementation of
                    //   the @Slf4j annotation first to make sure we aren't keeping a
                    //   strong reference to something that should be released.
                }
            }
        }

        void register(String key, Publisher publisher) {
            publishers.put(key, publisher);
        }

        Publisher get(String key) {
            return publishers.get(key);
        }
    }

    @Getter
    private final boolean emulated;

    private final String pubSubHost;
    private final TransportChannelProvider channelProvider;
    private final CredentialsProvider credentialsProvider;

    private final State state;
    private final Cleaner.Cleanable cleanable;

    /**
     * Creates a new one
     *
     * @param useEmulator if true, boots pubsub emulator locally if needed.
     *                    Otherwise, assumes this is running in an environment
     *                    where GCP pubsub resources are available
     */
    public PubSubConnectionManager(boolean useEmulator, String pubSubHost) throws IOException {
        this.emulated = useEmulator;
        this.pubSubHost = pubSubHost;

        this.channelProvider = emulatorPubSubChannelProvider();
        this.credentialsProvider = emulatorPubSubCredentialsProvider();

        var topicAdminClient = createTopicAdminClient();
        var subscriptionAdminClient = createSubscriptionAdminClient();

        var state = new State(topicAdminClient, subscriptionAdminClient);
        this.state = state;
        this.cleanable = cleaner.register(this, state);
    }

    /**
     * Creates a new client
     */
    private SubscriptionAdminClient createSubscriptionAdminClient() throws IOException {
        if (emulated) {
            var channelProvider = emulatorPubSubChannelProvider();
            var credentialsProvider = emulatorPubSubCredentialsProvider();

            var adminSettings = SubscriptionAdminSettings.newBuilder()
                    .setCredentialsProvider(credentialsProvider)
                    .setTransportChannelProvider(channelProvider)
                    .build();
            return SubscriptionAdminClient.create(adminSettings);
        } else {
            return SubscriptionAdminClient.create();
        }
    }

    /**
     * Fetches a Pub/Sub subscription, if it exists.
     * @param topicName the name of the subscription to fetch
     * @return the named Pub/Sub subscription, or null if it does not exist
     * @throws IOException
     */
    private Subscription getSubscription(ProjectSubscriptionName subscriptionName) throws IOException {
        var subscriptionAdminClient = this.state.getSubscriptionAdminClient();

        Subscription subscription;
        try {
            subscription = subscriptionAdminClient.getSubscription(subscriptionName);
        } catch (NotFoundException notFound) {
            subscription = null;
        }

        return subscription;
    }

    /**
     * Create a Pub/Sub subscription, if it does not already exist.
     * @param topicName the name of the subscription to create
     * @return the created Pub/Sub subscription, or null if it already exists
     * @throws IOException
     */
    private Subscription createSubscription(ProjectSubscriptionName subscriptionName, ProjectTopicName topicName) throws IOException {
        var subscriptionAdminClient = this.state.getSubscriptionAdminClient();

        Subscription subscription;

        try {
            subscription = subscriptionAdminClient.createSubscription(subscriptionName,
                    topicName,
                    PushConfig.getDefaultInstance(),
                    (int)ACK_DEADLINE_SECONDS);
        } catch (AlreadyExistsException alreadyExists) {
            // The subscription already exists
            //   Fail with a null to indicate nothing was created
            subscription = null;
        }

        return subscription;
    }

    /**
     * Fetches a Pub/Sub topic if it exists.
     * @param topicName the name of the topic to fetch
     * @return the named Pub/Sub topic, or null if it does not exist
     * @throws IOException
     */
    private Topic getTopic(ProjectTopicName topicName) throws IOException {
        var topicAdminClient = this.state.getTopicAdminClient();

        Topic topic;
        try {
            topic = topicAdminClient.getTopic(topicName);
        } catch (NotFoundException notFound) {
            topic = null;
        }

        return topic;
    }

    /**
     * Creates the Pub/Sub topic if it does not exist.
     * @param topicName the name of the topic to create
     * @return the created Pub/Sub Topic, or null if it already exists
     * @throws IOException
     */
    private Topic createTopic(ProjectTopicName topicName) throws IOException {
        var topicAdminClient = this.state.getTopicAdminClient();

        Topic topic = null;
        try {
            topic = topicAdminClient.createTopic(topicName);
        } catch (AlreadyExistsException alreadyExists) {
            // The subscription already exists
            //   Fail with a null to indicate nothing was created
            topic = null;
        }

        return topic;
    }

    /**
     * Returns a subscription for the given project and subscription.  If one exists,
     * it is re-used.  Otherwise, a new one is created.
     */
    public Subscription createSubscriptionIfNotExists(ProjectSubscriptionName projectSubscriptionName,
                                                      ProjectTopicName topicName) throws IOException {
        var subscription = getSubscription(projectSubscriptionName);

        if (subscription == null) {
            // The named subscription was not found, try and create it
            subscription = createSubscription(projectSubscriptionName, topicName);
        }

        return subscription;
    }

    /**
     * Creates the topic if it doesn't already exist.
     */
    public Topic createTopicIfNotExists(ProjectTopicName topicName) throws IOException {
        var topic = getTopic(topicName);

        if (topic == null) {
            // The named topic was not found, try and create it
            topic = createTopic(topicName);
        }

        return topic;
    }

    /**
     * Creates a new client.  Callers should be careful
     * to call {@link TopicAdminClient#close} when done
     * with the client to avoid resource leakage
     */
    private TopicAdminClient createTopicAdminClient() throws IOException {
        if (emulated) {
            return TopicAdminClient.create(TopicAdminSettings.newBuilder()
                    .setTransportChannelProvider(channelProvider)
                    .setCredentialsProvider(credentialsProvider)
                    .build());
        } else {
            return TopicAdminClient.create();
        }
    }

    /**
     * Creates a new publisher.  Callers should be careful to
     * call {@link Publisher#shutdown} when done with the publisher
     * to avoid resource leaks
     */
    public Publisher getOrCreatePublisher(ProjectTopicName topicName) throws IOException {
        var publisher = this.state.get(topicName.toString());

        if (publisher != null) {
            return publisher;
        }

        if (emulated) {
            publisher = Publisher.newBuilder(topicName)
                    .setChannelProvider(channelProvider)
                    .setCredentialsProvider(credentialsProvider)
                    .build();
        } else {
            publisher = Publisher.newBuilder(topicName).build();
        }

        // add the publisher to the list of things to shutdown for better cleanup
        this.state.register(topicName.toString(), publisher);
        return publisher;
    }

    /**
     * Creates the creds provider needed for the emulator.
     * @return
     */
    private CredentialsProvider emulatorPubSubCredentialsProvider() {
        return NoCredentialsProvider.create();
    }

    /**
     * Creates the channel need for the emulator
     * @return
     */
    private TransportChannelProvider emulatorPubSubChannelProvider() {
        assert StringUtils.isNotBlank(pubSubHost);

        var channel = ManagedChannelBuilder.forTarget(pubSubHost)
                .usePlaintext()
                .build();
        return FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel));
    }

    /**
     * Closes, releases, shuts down, and otherwise
     * cleans up things that might leak resources.
     */
    public void close() {
        cleanable.clean();
    }

    /**
     * Creates a new subscriber builder
     */
    public Subscriber.Builder subscribeBuilder(ProjectSubscriptionName projectSubscriptionName, MessageReceiver receiver) {
        if (emulated) {
            return Subscriber.newBuilder(projectSubscriptionName, receiver)
                    .setCredentialsProvider(credentialsProvider)
                    .setChannelProvider(channelProvider);
        } else {
            return Subscriber.newBuilder(projectSubscriptionName, receiver);
        }
    }

}
