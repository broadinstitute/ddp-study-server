package org.broadinstitute.ddp.exception;

public class MessageBuilderException extends DDPException {

    public MessageBuilderException(String message) {
        super(message);
    }

    public MessageBuilderException(String message, Throwable cause) {
        super(message, cause);
    }
}
