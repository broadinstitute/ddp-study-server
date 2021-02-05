package org.broadinstitute.ddp.event.dsmtask.api;

import org.broadinstitute.ddp.exception.DDPException;

/**
 * Base of the exceptions that come out of DsmTask processing.
 */
public class DsmTaskException extends DDPException {

    private final boolean needsToRetry;

    public DsmTaskException(String message) {
        this(message, false);
    }

    public DsmTaskException(String message, boolean needsToRetry) {
        super(message);
        this.needsToRetry = needsToRetry;
    }

    public boolean isNeedsToRetry() {
        return needsToRetry;
    }
}
