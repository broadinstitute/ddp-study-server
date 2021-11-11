package org.broadinstitute.ddp.event.publish;

/**
 * Interface defining an abstract 'publisher' for publishing of
 * a specified task to some 'queue' (for example a task for DSM to Google PubSub topic).
 */
public interface TaskPublisher {

    /**
     * Publish a specified event
     *
     * @param taskName        task type
     * @param payload         task payload
     * @param studyGuid       study guid
     * @param participantGuid participant guid
     */
    void publishTask(String taskName, String payload, String studyGuid, String participantGuid);
}
