package org.broadinstitute.ddp.housekeeping.message;

import org.broadinstitute.ddp.housekeeping.handler.MessageHandlingException;

/**
 * A thing that can handle a pubsub-derived message.
 * @param <T> the type of message
 */
@FunctionalInterface
public interface HousekeepingMessageHandler<T extends HousekeepingMessage> {

    void handleMessage(T message) throws MessageHandlingException;
}
