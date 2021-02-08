package org.broadinstitute.ddp.event.pubsubtask.api;

import org.broadinstitute.ddp.exception.DDPException;

/**
 * Base of the exceptions that come out of PubSubTask processing.
 */
public class PubSubTaskException extends DDPException {

    private final boolean needsToRetry;

    public PubSubTaskException(String message) {
        this(message, false);
    }

    public PubSubTaskException(String message, boolean needsToRetry) {
        super(message);
        this.needsToRetry = needsToRetry;
    }

    public boolean isNeedsToRetry() {
        return needsToRetry;
    }
}
