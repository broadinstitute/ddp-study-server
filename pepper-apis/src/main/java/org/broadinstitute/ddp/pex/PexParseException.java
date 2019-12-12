package org.broadinstitute.ddp.pex;

/**
 * Indicates a syntax error occurred while parsing the tokens from a pex expression.
 */
public class PexParseException extends PexException {

    /**
     * Instantiate PexParseException object.
     *
     * @param cause the underlying syntax error
     */
    public PexParseException(Throwable cause) {
        super(cause);
    }
}
