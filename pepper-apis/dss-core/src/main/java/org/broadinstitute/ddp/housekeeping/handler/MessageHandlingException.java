package org.broadinstitute.ddp.housekeeping.handler;

public class MessageHandlingException extends RuntimeException {

    private boolean shouldRetry;

    public MessageHandlingException(String message, boolean shouldRetry) {
        super(message);
        this.shouldRetry = shouldRetry;
    }

    public MessageHandlingException(String message, Exception cause, boolean shouldRetry) {
        super(message, cause);
        this.shouldRetry = shouldRetry;
    }

    /**
     * Returns true if the message should be re-delivered
     * for a subsuequent attempt, false if no re-delvery
     * should be done
     */
    public boolean shouldRetry() {
        return shouldRetry;
    }
}
