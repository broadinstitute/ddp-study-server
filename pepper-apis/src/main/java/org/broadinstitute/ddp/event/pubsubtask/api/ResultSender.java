package org.broadinstitute.ddp.event.pubsubtask.api;

public interface ResultSender {

    void sendPubSubTaskResult(PubSubTaskResult pubSubTaskResult);
}

