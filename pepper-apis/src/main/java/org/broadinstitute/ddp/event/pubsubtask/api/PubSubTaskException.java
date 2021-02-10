package org.broadinstitute.ddp.event.pubsubtask.api;

import org.broadinstitute.ddp.exception.DDPException;

/**
 * Base of the exceptions that come out of PubSubTask processing.
 */
public class PubSubTaskException extends DDPException {

    private final boolean needsToRetry;
    private final PubSubTask pubSubTask;

    public PubSubTaskException(String message) {
        this(message, null, false);
    }

    public PubSubTaskException(String message, boolean needsToRetry) {
        this(message, null, needsToRetry);
    }

    public PubSubTaskException(String message, PubSubTask pubSubTask) {
        this(message, pubSubTask, false);
    }

    public PubSubTaskException(String message, PubSubTask pubSubTask, boolean needsToRetry) {
        super(message);
        this.pubSubTask = pubSubTask;
        this.needsToRetry = needsToRetry;
    }

    public boolean isNeedsToRetry() {
        return needsToRetry;
    }

    public PubSubTask getPubSubTask() {
        return pubSubTask;
    }
}
