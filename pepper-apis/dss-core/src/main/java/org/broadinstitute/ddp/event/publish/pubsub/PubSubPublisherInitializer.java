package org.broadinstitute.ddp.event.publish.pubsub;

import static java.lang.String.format;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.cloud.pubsub.v1.Publisher;
import com.google.common.annotations.VisibleForTesting;
import com.google.pubsub.v1.ProjectTopicName;
import com.typesafe.config.Config;

import lombok.Value;

import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.housekeeping.PubSubConnectionManager;
import org.broadinstitute.ddp.util.ConfigManager;
import org.broadinstitute.ddp.util.ConfigUtil;

/**
 * Initialize PubSub publishers for different topics.
 */
@Value
public class PubSubPublisherInitializer {
    private static class InstanceWrapper {
        private static final PubSubPublisherInitializer INSTANCE = new PubSubPublisherInitializer(); 
    }

    private final Config config;
    private final PubSubConnectionManager connectionManager;
    private final Map<String, Publisher> publishers = new ConcurrentHashMap<>();

    private PubSubPublisherInitializer() {
        this.config = ConfigManager.getInstance().getConfig();

        final var usePubSubEmulator = ConfigUtil.getBoolOrElse(config, ConfigFile.USE_PUBSUB_EMULATOR, false);
        final var pubSubHost = ConfigUtil.getStrIfPresent(config, ConfigFile.PUBSUB_HOST);

        try {
            this.connectionManager = new PubSubConnectionManager(usePubSubEmulator, pubSubHost);
        } catch (IOException error) {
            throw new DDPException("failed to create the Pub/Sub connection manager.", error);
        }
    }

    public static Publisher getOrCreatePublisher(String pubSubTopicName) {
        final var instance = InstanceWrapper.INSTANCE;

        var publishers = instance.getPublishers();
        final var connectionManager = instance.getConnectionManager();
        final var projectId = instance.getConfig().getString(ConfigFile.GOOGLE_PROJECT_ID);

        return publishers.computeIfAbsent(pubSubTopicName, key -> {
            try {
                return connectionManager.getOrCreatePublisher(
                        ProjectTopicName.of(projectId, pubSubTopicName));
            } catch (IOException e) {
                throw new DDPException(format("Error during creation of PubSub publisher for topic %s", key), e);
            }
        });
    }

    public static PubSubConnectionManager getPubsubConnectionManager() {
        return InstanceWrapper.INSTANCE.getConnectionManager();
    }

    @VisibleForTesting
    public static void setPublisher(String pubsubTopicName, Publisher publisher) {
        final var publishers = InstanceWrapper.INSTANCE.getPublishers();

        if (publisher == null) {
            publishers.remove(pubsubTopicName);
        } else {
            publishers.put(pubsubTopicName, publisher);
        }
    }
}
