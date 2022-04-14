package org.broadinstitute.ddp.exception;

public class NoSendableEmailAddressException extends MessageBuilderException {

    public NoSendableEmailAddressException(String message) {
        super(message);
    }

    public NoSendableEmailAddressException(String message, Throwable cause) {
        super(message, cause);
    }
}
