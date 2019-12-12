package org.broadinstitute.ddp.exception;

public class DDPTokenException extends DDPException {

    public DDPTokenException(Throwable cause) {
        super(cause);
    }

    public DDPTokenException(String message) {
        super(message);
    }

    public DDPTokenException(String message, Throwable cause) {
        super(message, cause);
    }

}
