package org.broadinstitute.ddp.event.pubsubtask.api;

import org.broadinstitute.ddp.exception.DDPException;

/**
 * Exception that come out of PubSubTask processing API.
 */
public class PubSubTaskException extends DDPException {

    private final boolean shouldRetry;
    private final PubSubTask pubSubTask;

    public PubSubTaskException(String message) {
        this(message, null, null, false);
    }

    public PubSubTaskException(String message, Throwable e) {
        this(message, e, null, false);
    }

    public PubSubTaskException(String message, boolean shouldRetry) {
        this(message, null, null, shouldRetry);
    }

    public PubSubTaskException(String message, PubSubTask pubSubTask) {
        this(message, null, pubSubTask, false);
    }

    public PubSubTaskException(String message, Throwable e, PubSubTask pubSubTask, boolean shouldRetry) {
        super(message, e);
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
