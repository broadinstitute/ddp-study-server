package org.broadinstitute.ddp.pex;

/**
 * Indicates a pex expression tried to use a feature that is not supported,
 * perhaps performing a predicate test on data with a mismatched type.
 */
public class PexUnsupportedException extends PexException {

    /**
     * Instantiate PexUnsupportedException object.
     *
     * @param message the unsupported explanation
     */
    public PexUnsupportedException(String message) {
        super(message);
    }

    public PexUnsupportedException(String message, Throwable cause) {
        super(message, cause);
    }
}
