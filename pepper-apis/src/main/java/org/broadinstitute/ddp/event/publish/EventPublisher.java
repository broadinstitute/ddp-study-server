package org.broadinstitute.ddp.event.publish;

/**
 * Interface defining an abstract 'publisher' for publishing of
 * a specified avent to some 'queue' (for example to Google PubSub topic).
 */
public interface EventPublisher {

    /**
     * Publish a specified event
     *
     * @param eventType       event type
     * @param payload         event payload
     * @param studyGuid       study guid
     * @param participantGuid participant guid
     */
    void publishEvent(String eventType, String payload, String studyGuid, String participantGuid);
}
