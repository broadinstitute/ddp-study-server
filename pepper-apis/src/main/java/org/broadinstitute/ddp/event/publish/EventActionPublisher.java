package org.broadinstitute.ddp.event.publish;

import org.broadinstitute.ddp.model.activity.types.EventActionType;

/**
 * Interface defining an abstract 'publisher' for publishing of
 * a specified event to some 'queue' (for example to Google PubSub topic).
 */
public interface EventActionPublisher {

    /**
     * Publish a specified event
     * @param eventActionType event type
     * @param eventPayload event payload (containing event attributes)
     * @param studyGuid study guid
     * @param participantGuid participant guid
     */
    void publishEventAction(
            EventActionType eventActionType,
            String eventPayload,
            String studyGuid,
            String participantGuid);

}
