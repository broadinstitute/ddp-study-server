package org.broadinstitute.ddp.event.pubsubtask.api;

/**
 * Interface defining a processor which should do actions
 * in response to PubSubTask published to special pubsub topic
 * (DSS subscribed to this topic and dubscription configured by config param "pubsub.pubSubTasksSubscription").
 */
@FunctionalInterface
public interface PubSubTaskProcessor {

    PubSubTaskProcessorResult processPubSubTask(PubSubTask pubSubTask);

    class PubSubTaskProcessorResult {

        private final PubSubTaskResult pubSubTaskResult;
        private final boolean needsToRetry;

        public PubSubTaskProcessorResult(PubSubTaskResult pubSubTaskResult, boolean needsToRetry) {
            this.pubSubTaskResult = pubSubTaskResult;
            this.needsToRetry = needsToRetry;
        }

        public PubSubTaskResult getPubSubTaskResult() {
            return pubSubTaskResult;
        }

        public boolean isNeedsToRetry() {
            return needsToRetry;
        }
    }
}
