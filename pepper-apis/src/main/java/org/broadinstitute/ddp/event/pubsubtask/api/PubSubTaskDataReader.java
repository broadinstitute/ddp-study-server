package org.broadinstitute.ddp.event.pubsubtask.api;

import java.util.Properties;

/**
 * Reads a task-specific attributes and parses a task-specific payload.
 * The readerhould be defined for a certain taskType {@link PubSubTaskProcessorFactory}.
 * The message to be parsed received from subscription
 * defined by config param "pubsub.pubSubTasksSubscription".
 * The fetched attributes set to {@link PubSubTask#getAttributes()}.
 * An object with parsed payload is returned from the method.
 * If error happens during parsing then {@link PubSubTaskException} will be thrown.
 */
@FunctionalInterface
public interface PubSubTaskDataReader {

    PubSubTaskPayloadData readTaskData(PubSubTask pubSubTask, Class<?> payloadClass);

    class PubSubTaskPayloadData {
        private final Properties properties;
        private final Object payloadObj;

        public PubSubTaskPayloadData(Properties properties, Object payloadObj) {
            this.properties = properties;
            this.payloadObj = payloadObj;
        }

        public Properties getProperties() {
            return properties;
        }

        public Object getPayloadObj() {
            return payloadObj;
        }
    }
}
