package org.broadinstitute.ddp.event.pubsubtask.api;

/**
 * Interface defining a processor which should do actions
 * in response to PubSubTask published to special pubsub topic
 * (DSS subscribed to this topic and subscription configured by config param "pubsub.pubSubTasksSubscription").
 */
@FunctionalInterface
public interface PubSubTaskProcessor {

    PubSubTaskProcessorResult processPubSubTask(PubSubTask pubSubTask);

    /**
     * Result of {@link PubSubTask} processing.
     */
    class PubSubTaskProcessorResult {

        private final PubSubTaskResult pubSubTaskResult;
        private final boolean shouldRetry;

        public PubSubTaskProcessorResult(PubSubTaskResult pubSubTaskResult, boolean shouldRetry) {
            this.pubSubTaskResult = pubSubTaskResult;
            this.shouldRetry = shouldRetry;
        }

        public PubSubTaskResult getPubSubTaskResult() {
            return pubSubTaskResult;
        }

        public boolean isShouldRetry() {
            return shouldRetry;
        }
    }
}
