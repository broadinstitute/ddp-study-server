package org.broadinstitute.ddp.pex;

/**
 * Indicates a database error occurred while fetching data required to evaluate a pex expression.
 */
public class PexFetchException extends PexRuntimeException {

    public PexFetchException(String message) {
        super(message);
    }

    /**
     * Instantiate PexFetchException object.
     *
     * @param cause the database error
     */
    public PexFetchException(Throwable cause) {
        super(cause);
    }

    /**
     * Instantiate PexFetchException object with message.
     *
     * @param message the error description
     * @param cause   the database error
     */
    public PexFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
