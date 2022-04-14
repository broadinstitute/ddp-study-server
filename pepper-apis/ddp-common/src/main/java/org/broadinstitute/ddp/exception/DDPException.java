package org.broadinstitute.ddp.exception;

/**
 * Root of the hierarchy for DDP-specific exceptions.
 *
 * <p>This and all subclass exceptions are unchecked exceptions, meaning they do not have to be
 * declared in throws clauses. But they should still be handled, or they will get bubbled up
 * the call stack.
 *
 * <p>If you are looking to handle an exception from DDP but doesn't care which one, then catch this
 * root exception class.
 */
public class DDPException extends RuntimeException {

    public DDPException() {
    }

    public DDPException(String message) {
        super(message);
    }

    public DDPException(String message, Throwable cause) {
        super(message, cause);
    }

    public DDPException(Throwable cause) {
        super(cause);
    }
}
