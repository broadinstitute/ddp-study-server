package org.broadinstitute.ddp.event.pubsubtask.api;

import org.broadinstitute.ddp.exception.DDPException;

/**
 * Base of the exceptions that come out of PubSubTask processing.
 */
public class PubSubTaskException extends DDPException {

    private final boolean shouldRetry;
    private final PubSubTask pubSubTask;

    public PubSubTaskException(String message) {
        this(message, null, false);
    }

    public PubSubTaskException(String message, boolean shouldRetry) {
        this(message, null, shouldRetry);
    }

    public PubSubTaskException(String message, PubSubTask pubSubTask) {
        this(message, pubSubTask, false);
    }

    public PubSubTaskException(String message, PubSubTask pubSubTask, boolean shouldRetry) {
        super(message);
        this.pubSubTask = pubSubTask;
        this.shouldRetry = shouldRetry;
    }

    public boolean isShouldRetry() {
        return shouldRetry;
    }

    public PubSubTask getPubSubTask() {
        return pubSubTask;
    }
}
