package org.broadinstitute.ddp.exception;

public class DDPInternalError extends RuntimeException {
    public DDPInternalError(String message) {
        super(message);
    }

    public DDPInternalError(String message, Throwable e) {
        super(message, e);
    }
}
