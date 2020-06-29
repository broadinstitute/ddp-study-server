package org.broadinstitute.ddp.exception;

public class MessageBuilderException extends DDPException {

    private boolean shouldDeleteEvent;

    public MessageBuilderException(String message) {
        this(false, message);
    }

    public MessageBuilderException(String message, Throwable cause) {
        this(false, message, cause);
    }

    public MessageBuilderException(boolean shouldDeleteEvent, String message) {
        super(message);
        this.shouldDeleteEvent = shouldDeleteEvent;
    }

    public MessageBuilderException(boolean shouldDeleteEvent, String message, Throwable cause) {
        super(message, cause);
        this.shouldDeleteEvent = shouldDeleteEvent;
    }

    public boolean shouldDeleteEvent() {
        return shouldDeleteEvent;
    }
}
