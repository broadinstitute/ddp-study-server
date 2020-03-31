package org.broadinstitute.ddp.pex;

/**
 * Indicates a general error occurred while evaluating a pex expression.
 */
public class PexRuntimeException extends PexException {

    public PexRuntimeException(String message) {
        super(message);
    }

    public PexRuntimeException(Throwable cause) {
        super(cause);
    }

    public PexRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }
}
