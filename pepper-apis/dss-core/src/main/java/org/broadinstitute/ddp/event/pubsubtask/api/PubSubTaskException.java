package org.broadinstitute.ddp.event.pubsubtask.api;

import org.broadinstitute.ddp.exception.DDPException;

/**
 * Exception that come out of PubSubTask processing API.
 */
public class PubSubTaskException extends DDPException {

    public enum Severity {
        ERROR,
        WARN
    }

    private final Severity severity;
    private final boolean shouldRetry;
    private final PubSubTask pubSubTask;

    public PubSubTaskException(String message, Throwable e) {
        this(message, e, null, null, false);
    }

    public PubSubTaskException(String message, Severity severity) {
        this(message, severity, false);
    }

    public PubSubTaskException(String message, Severity severity, PubSubTask pubSubTask) {
        this(message, null, severity, pubSubTask, false);
    }

    public PubSubTaskException(String message, Severity severity, boolean shouldRetry) {
        this(message, null, severity, null, shouldRetry);
    }

    public PubSubTaskException(String message, Throwable e, Severity severity, PubSubTask pubSubTask, boolean shouldRetry) {
        super(message, e);
        this.severity = severity;
        this.pubSubTask = pubSubTask;
        this.shouldRetry = shouldRetry;
    }


    public boolean isShouldRetry() {
        return shouldRetry;
    }

    public Severity getSeverity() {
        return severity;
    }

    public PubSubTask getPubSubTask() {
        return pubSubTask;
    }
}
