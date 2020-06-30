package org.broadinstitute.ddp.exception;

public class NoSendableEmailException extends MessageBuilderException {

    public NoSendableEmailException(String message) {
        super(message);
    }

    public NoSendableEmailException(String message, Throwable cause) {
        super(message, cause);
    }
}
