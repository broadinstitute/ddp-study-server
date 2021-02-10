package org.broadinstitute.ddp.event.pubsubtask.api;

import org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskDataReader.PubSubTaskPayloadData;

/**
 * Interface defining a processor which should do actions
 * in response to PubSubTask published to special pubsub topic
 * (DSS subscribed to this topic and subscription configured by config param "pubsub.pubSubTasksSubscription").
 */
@FunctionalInterface
public interface PubSubTaskProcessor {

    PubSubTaskProcessorResult processPubSubTask(PubSubTask pubSubTask, PubSubTaskPayloadData payloadData);

    /**
     * Result of {@link PubSubTask} processing.
     */
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
