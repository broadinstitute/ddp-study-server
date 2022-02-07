package org.broadinstitute.ddp.housekeeping.message;

/**
 * Currently a marker interface; will expand this as more
 * {@link HousekeepingMessageHandler}s come online
 */
public interface HousekeepingMessage {

    /**
     * Returns the event_configuration_id
     */
    long getEventConfigurationId();

}
