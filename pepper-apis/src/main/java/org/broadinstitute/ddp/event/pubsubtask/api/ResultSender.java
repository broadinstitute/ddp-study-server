package org.broadinstitute.ddp.event.pubsubtask.api;

/**
 * Interface providing possibility to define custom
 * sender for {@link PubSubTaskResult}.
 *
 */
public interface ResultSender {

    void sendPubSubTaskResult(PubSubTaskResult pubSubTaskResult);
}

