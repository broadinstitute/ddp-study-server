package org.broadinstitute.ddp.pex;

import org.broadinstitute.ddp.exception.DDPException;

/**
 * Base of the exceptions that come out of the pex interpreter.
 */
public class PexException extends DDPException {

    public PexException() {}

    public PexException(String message) {
        super(message);
    }

    public PexException(String message, Throwable cause) {
        super(message, cause);
    }

    public PexException(Throwable cause) {
        super(cause);
    }
}
