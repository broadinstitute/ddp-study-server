package org.broadinstitute.ddp.event.pubsubtask.api;

/**
 * Factory for registering {@link PubSubTaskProcessor}'s
 * for each of known PubSubTask taskType's.
 */
public interface PubSubTaskProcessorFactory {

    PubSubTaskDescriptor getPubSubTaskDescriptor(String pubSubTaskType);

    /**
     * Holds data which necessary for {@link PubSubTask} processing
     */
    class PubSubTaskDescriptor {
        private final PubSubTaskProcessor pubSubTaskProcessor;

        public PubSubTaskDescriptor(PubSubTaskProcessor pubSubTaskProcessor) {
            this.pubSubTaskProcessor = pubSubTaskProcessor;
        }

        public PubSubTaskProcessor getPubSubTaskProcessor() {
            return pubSubTaskProcessor;
        }
    }
}
