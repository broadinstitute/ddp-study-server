package org.broadinstitute.ddp.event.pubsubtask.api;

/**
 * Interface defining a processor which should do actions
 * in response to PubSubTask published to special pubsub topic
 * (DSS subscribed to this topic and subscription configured by config param "pubsub.pubSubTasksSubscription").
 */
@FunctionalInterface
public interface PubSubTaskProcessor {

    PubSubTaskResult processPubSubTask(PubSubTask pubSubTask);
}
