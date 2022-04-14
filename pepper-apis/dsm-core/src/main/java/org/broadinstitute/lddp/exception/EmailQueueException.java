package org.broadinstitute.lddp.exception;

public class EmailQueueException extends RuntimeException {
    public EmailQueueException(String message) {
        super(message);
    }

    public EmailQueueException(String message, Throwable cause) {
        super(message, cause);
    }
}
