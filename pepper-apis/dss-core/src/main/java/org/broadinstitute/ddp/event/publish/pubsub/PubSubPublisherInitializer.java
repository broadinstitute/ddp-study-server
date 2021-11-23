package org.broadinstitute.ddp.event.publish.pubsub;

import static java.lang.String.format;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import com.google.cloud.pubsub.v1.Publisher;
import com.google.common.annotations.VisibleForTesting;
import com.google.pubsub.v1.ProjectTopicName;
import com.typesafe.config.Config;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.housekeeping.PubSubConnectionManager;
import org.broadinstitute.ddp.util.ConfigManager;

/**
 * Initialize PubSub publishers for different topics.
 */
public class PubSubPublisherInitializer {

    private static ConcurrentHashMap<String, Publisher> pubSubPublisherDataMap = new ConcurrentHashMap<>();

    private static final Config conf = ConfigManager.getInstance().getConfig();
    private static final PubSubConnectionManager pubsubConnectionManager = new PubSubConnectionManager(
            conf.getBoolean(ConfigFile.USE_PUBSUB_EMULATOR));

    public static Publisher getOrCreatePublisher(String pubSubTopicName) {
        return pubSubPublisherDataMap.computeIfAbsent(pubSubTopicName, key -> {
            try {
                return pubsubConnectionManager.getOrCreatePublisher(
                        ProjectTopicName.of(conf.getString(ConfigFile.GOOGLE_PROJECT_ID), pubSubTopicName));
            } catch (IOException e) {
                throw new DDPException(format("Error during creation of PubSub publisher for topic %s", key), e);
            }
        });
    }

    public static PubSubConnectionManager getPubsubConnectionManager() {
        return pubsubConnectionManager;
    }

    @VisibleForTesting
    public static void setPublisher(String pubsubTopicName, Publisher publisher) {
        if (publisher == null) {
            pubSubPublisherDataMap.remove(pubsubTopicName);
        } else {
            pubSubPublisherDataMap.put(pubsubTopicName, publisher);
        }
    }
}
