package org.broadinstitute.ddp.event.pubsubtask.api;

/**
 * Factory for registering {@link PubSubTaskProcessor}'s
 * for each of known PubSubTask taskType's.
 */
public interface PubSubTaskProcessorFactory {

    PubSubTaskDescriptor getPubSubTaskDescriptors(String pubSubTaskType);

    /**
     * Holds data which necessary for {@link PubSubTask} processing
     */
    class PubSubTaskDescriptor {
        private final PubSubTaskProcessor pubSubTaskProcessor;
        private final Class<?> payloadClass;

        public PubSubTaskDescriptor(PubSubTaskProcessor pubSubTaskProcessor, Class<?> payloadClass) {
            this.pubSubTaskProcessor = pubSubTaskProcessor;
            this.payloadClass = payloadClass;
        }

        public PubSubTaskProcessor getPubSubTaskProcessor() {
            return pubSubTaskProcessor;
        }

        public Class<?> getPayloadClass() {
            return payloadClass;
        }
    }
}
